package com.hedera.hashgraph.sdk;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519Signature;
import com.hedera.hashgraph.sdk.proto.*;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.netty.shaded.io.netty.util.concurrent.GlobalEventExecutor;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Transaction extends HederaCall<com.hedera.hashgraph.sdk.proto.Transaction, TransactionResponse, TransactionId> {

    private final io.grpc.MethodDescriptor<com.hedera.hashgraph.sdk.proto.Transaction, com.hedera.hashgraph.sdk.proto.TransactionResponse> methodDescriptor;
    final com.hedera.hashgraph.sdk.proto.Transaction.Builder inner;
    final com.hedera.hashgraph.sdk.proto.AccountID nodeAccountId;
    final com.hedera.hashgraph.sdk.proto.TransactionID transactionId;

    @Nullable
    private final Client client;

    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final int PREFIX_LEN = 6;

    Transaction(
        @Nullable Client client,
        com.hedera.hashgraph.sdk.proto.Transaction.Builder inner,
        com.hedera.hashgraph.sdk.proto.AccountID nodeAccountId,
        com.hedera.hashgraph.sdk.proto.TransactionID transactionId,
        MethodDescriptor<com.hedera.hashgraph.sdk.proto.Transaction, TransactionResponse> methodDescriptor
    ) {
        super();
        this.client = client;
        this.inner = inner;
        this.nodeAccountId = nodeAccountId;
        this.transactionId = transactionId;
        this.methodDescriptor = methodDescriptor;
    }

    public static Transaction fromBytes(Client client, byte[] bytes) throws InvalidProtocolBufferException {
        var inner = com.hedera.hashgraph.sdk.proto.Transaction.parseFrom(bytes);
        var body = TransactionBody.parseFrom(inner.getBodyBytes());

        return new Transaction(client, inner.toBuilder(), body.getNodeAccountID(), body.getTransactionID(), methodForTxnBody(body));
    }

    public Transaction sign(Ed25519PrivateKey privateKey) {
        var pubKey = ByteString.copyFrom(
            privateKey.getPublicKey()
                .toBytes()
        );

        var sigMap = inner.getSigMapBuilder();

        for (int i = 0; i < sigMap.getSigPairCount(); i++) {
            var pubKeyPrefix = sigMap.getSigPair(i)
                .getPubKeyPrefix();

            if (pubKey.startsWith(pubKeyPrefix)) {
                throw new IllegalArgumentException("transaction already signed with key: " + privateKey.toString());
            }
        }

        var signature = Ed25519Signature.forMessage(
            privateKey,
            inner.getBodyBytes()
                .toByteArray()
        )
            .toBytes();

        sigMap.addSigPair(
            SignaturePair.newBuilder()
                .setPubKeyPrefix(pubKey)
                .setEd25519(ByteString.copyFrom(signature))
                .build()
        );

        return this;
    }

    public TransactionId getId() {
        return new TransactionId(transactionId);
    }

    @Override
    public com.hedera.hashgraph.sdk.proto.Transaction toProto() {
        validate();
        return inner.build();
    }

    @Override
    protected MethodDescriptor<com.hedera.hashgraph.sdk.proto.Transaction, TransactionResponse> getMethod() {
        return methodDescriptor;
    }

    @Override
    protected Channel getChannel() {
        Objects.requireNonNull(client, "Transaction.client must be non-null in regular use");

        var channel = client.getNodeForId(new AccountId(nodeAccountId));
        Objects.requireNonNull(channel, "Transaction.nodeAccountId not found on Client");

        return channel.getChannel();
    }

    @Override
    protected void validate() {
        var sigMap = inner.getSigMapOrBuilder();

        if (sigMap.getSigPairCount() < 2) {
            if (sigMap.getSigPairCount() == 0) {
                addValidationError("Transaction requires at least one signature");
            } // else contains one signature which is fine
        } else {
            var publicKeys = new HashSet<>();

            for (int i = 0; i < sigMap.getSigPairCount(); i++) {
                var sig = sigMap.getSigPairOrBuilder(i);
                ByteString pubKeyPrefix = sig.getPubKeyPrefix();

                if (!publicKeys.add(pubKeyPrefix)) {
                    addValidationError("duplicate signing key: " + Hex.toHexString(getPrefix(pubKeyPrefix).toByteArray()) + "...");
                }
            }
        }

        checkValidationErrors("Transaction failed validation");
    }

    @Override
    protected TransactionId mapResponse(TransactionResponse response) throws HederaException {
        HederaException.throwIfExceptional(response.getNodeTransactionPrecheckCode());
        return new TransactionId(transactionId);
    }

    private <T> T executeAndWaitFor(CheckedFunction<TransactionId, T> execute, Function<T, TransactionReceipt> mapReceipt)
            throws HederaException, HederaNetworkException {
        var id = execute();

        T response = null;
        ResponseCodeEnum receiptStatus = ResponseCodeEnum.UNRECOGNIZED;

        for (int attempt = 1; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            response = execute.apply(id);
            receiptStatus = mapReceipt.apply(response)
                .getStatus();

            // if we're fetching a record it returns `RECORD_NOT_FOUND` instead of `UNKNOWN`
            if (receiptStatus == ResponseCodeEnum.UNKNOWN ||
                receiptStatus == ResponseCodeEnum.RECEIPT_NOT_FOUND ||
                receiptStatus == ResponseCodeEnum.RECORD_NOT_FOUND) {
                // If the receipt is UNKNOWN this means that the server has not finished
                // processing the transaction

                try {
                    Thread.sleep(1500 * attempt);
                } catch (InterruptedException e) {
                    break;
                }
            } else {
                // Otherwise either the receipt is SUCCESS or there is something _exceptional_ wrong
                break;
            }
        }

        HederaException.throwIfExceptional(receiptStatus);
        return Objects.requireNonNull(response);
    }

    public final TransactionReceipt executeForReceipt() throws HederaException, HederaNetworkException {
        return executeAndWaitFor(
            id -> new TransactionReceiptQuery(getClient()).setTransactionId(id)
                .execute(),
            receipt -> receipt
        );
    }

    public TransactionRecord executeForRecord() throws HederaException, HederaNetworkException {
        checkGenerateRecordIsSet();

        return executeAndWaitFor(
            id -> new TransactionRecordQuery(getClient()).setTransactionId(id)
                .execute(),
            TransactionRecord::getReceipt
        );
    }

    private void checkGenerateRecordIsSet() {
        // we have to re-decode the transaction body to ensure this flag is set
        TransactionBody txnBody;
        try {
            txnBody = TransactionBody.parseFrom(inner.getBodyBytes());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("transaction is not parseable", e);
        }

        if (!txnBody.getGenerateRecord()) {
            throw new IllegalStateException(
                "Getting a record from a Transaction requires `setGenerateRecord(true)` before it is built and signed"
            );
        }
    }

    public void executeForReceiptAsync(Consumer<TransactionReceipt> onSuccess, Consumer<HederaThrowable> onError) {
        var handler = new AsyncRetryHandler<>(
                (id, onSuccess_, onError_) -> new TransactionReceiptQuery(getClient()).setTransactionId(id)
                    .executeAsync(onSuccess_, onError_),
                receipt -> receipt, onSuccess, onError
        );

        executeAsync(handler::waitFor, onError);
    }

    public void executeForRecordAsync(Consumer<TransactionRecord> onSuccess, Consumer<HederaThrowable> onError) {
        checkGenerateRecordIsSet();

        var handler = new AsyncRetryHandler<>(
                (id, onSuccess_, onError_) -> new TransactionRecordQuery(getClient()).setTransactionId(id)
                    .executeAsync(onSuccess_, onError_),
                TransactionRecord::getReceipt, onSuccess, onError
        );

        executeAsync(handler::waitFor, onError);
    }

    public byte[] toBytes() {
        return toProto().toByteArray();
    }

    private Client getClient() {
        return Objects.requireNonNull(client);
    }

    private static class AsyncRetryHandler<T> {
        private final ExecuteAsync<T> execute;
        private final Function<T, TransactionReceipt> mapReceipt;
        private final Consumer<T> onSuccess;
        private final Consumer<HederaThrowable> onError;

        private int attemptsLeft = MAX_RETRY_ATTEMPTS;

        private AsyncRetryHandler(
            ExecuteAsync<T> execute,
            Function<T, TransactionReceipt> mapReceipt,
            Consumer<T> onSuccess,
            Consumer<HederaThrowable> onError
        ) {
            this.execute = execute;
            this.mapReceipt = mapReceipt;
            this.onSuccess = onSuccess;
            this.onError = onError;
        }

        private void waitFor(final TransactionId id) {
            attemptsLeft -= 1;
            execute.executeAsync(id, result -> handleResult(id, result), onError);
        }

        private void handleResult(TransactionId id, T result) {
            var receipt = mapReceipt.apply(result);
            var resultCode = receipt.getStatus();

            if (resultCode == ResponseCodeEnum.UNKNOWN) {
                if (attemptsLeft == 0) {
                    onError.accept(new HederaException(ResponseCodeEnum.UNKNOWN));
                    return;
                }

                GlobalEventExecutor.INSTANCE.schedule(() -> waitFor(id), 1500, TimeUnit.MILLISECONDS);
            } else if (HederaException.isCodeExceptional(resultCode, false)) {
                onError.accept(new HederaException(resultCode));
            } else {
                onSuccess.accept(result);
            }
        }
    }

    @FunctionalInterface
    private interface ExecuteAsync<T> {
        void executeAsync(TransactionId id, Consumer<T> onSuccess, Consumer<HederaThrowable> onError);
    }

    private static ByteString getPrefix(ByteString byteString) {
        if (byteString.size() <= PREFIX_LEN) {
            return byteString;
        }

        return byteString.substring(0, PREFIX_LEN);
    }

    private static MethodDescriptor<com.hedera.hashgraph.sdk.proto.Transaction, TransactionResponse> methodForTxnBody(TransactionBodyOrBuilder body) {
        switch (body.getDataCase()) {
        case ADMINDELETE:
            return FileServiceGrpc.getAdminDeleteMethod();
        case ADMINUNDELETE:
            return FileServiceGrpc.getAdminUndeleteMethod();
        case CONTRACTCALL:
            return SmartContractServiceGrpc.getContractCallMethodMethod();
        case CONTRACTCREATEINSTANCE:
            return SmartContractServiceGrpc.getCreateContractMethod();
        case CONTRACTUPDATEINSTANCE:
            return SmartContractServiceGrpc.getUpdateContractMethod();
        case CRYPTOADDCLAIM:
            return CryptoServiceGrpc.getAddClaimMethod();
        case CRYPTOCREATEACCOUNT:
            return CryptoServiceGrpc.getCreateAccountMethod();
        case CRYPTODELETE:
            return CryptoServiceGrpc.getCryptoDeleteMethod();
        case CRYPTODELETECLAIM:
            return CryptoServiceGrpc.getDeleteClaimMethod();
        case CRYPTOTRANSFER:
            return CryptoServiceGrpc.getCryptoTransferMethod();
        case CRYPTOUPDATEACCOUNT:
            return CryptoServiceGrpc.getUpdateAccountMethod();
        case FILEAPPEND:
            return FileServiceGrpc.getAppendContentMethod();
        case FILECREATE:
            return FileServiceGrpc.getCreateFileMethod();
        case FILEDELETE:
            return FileServiceGrpc.getDeleteFileMethod();
        case FILEUPDATE:
            return FileServiceGrpc.getUpdateFileMethod();
        case DATA_NOT_SET:
            throw new IllegalArgumentException("method not set");
        default:
            throw new IllegalArgumentException("unsupported method");
        }
    }
}
