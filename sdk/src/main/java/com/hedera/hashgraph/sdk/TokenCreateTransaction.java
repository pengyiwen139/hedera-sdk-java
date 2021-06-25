package com.hedera.hashgraph.sdk;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.proto.*;
import com.hedera.hashgraph.sdk.proto.TransactionResponse;
import io.grpc.MethodDescriptor;

import java.time.Duration;
import java.time.Instant;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;

public class TokenCreateTransaction extends Transaction<TokenCreateTransaction> {
    private final TokenCreateTransactionBody.Builder builder;

    AccountId treasuryAccountId;
    AccountId autoRenewAccountId;

    public TokenCreateTransaction() {
        builder = TokenCreateTransactionBody.newBuilder();

        setAutoRenewPeriod(DEFAULT_AUTO_RENEW_PERIOD);
        setMaxTransactionFee(new Hbar(30));
    }

    TokenCreateTransaction(LinkedHashMap<TransactionId, LinkedHashMap<AccountId, com.hedera.hashgraph.sdk.proto.Transaction>> txs) throws InvalidProtocolBufferException {
        super(txs);

        builder = bodyBuilder.getTokenCreation().toBuilder();

        if (builder.hasTreasury()) {
            treasuryAccountId = AccountId.fromProtobuf(builder.getTreasury());
        }
    }

    TokenCreateTransaction(com.hedera.hashgraph.sdk.proto.TransactionBody txBody) {
        super(txBody);

        builder = bodyBuilder.getTokenCreation().toBuilder();

        if (builder.hasTreasury()) {
            treasuryAccountId = AccountId.fromProtobuf(builder.getTreasury());
        }
    }

    public String getTokenName() {
        return builder.getName();
    }

    public TokenCreateTransaction setTokenName(String name) {
        requireNotFrozen();
        builder.setName(name);
        return this;
    }

    public String getTokenSymbol() {
        return builder.getSymbol();
    }

    public TokenCreateTransaction setTokenSymbol(String symbol) {
        requireNotFrozen();
        builder.setSymbol(symbol);
        return this;
    }

    public int getDecimals() {
        return builder.getDecimals();
    }

    public TokenCreateTransaction setDecimals(int decimals) {
        requireNotFrozen();
        builder.setDecimals(decimals);
        return this;
    }

    public long getInitialSupply() {
        return builder.getInitialSupply();
    }

    public TokenCreateTransaction setInitialSupply(long initialSupply) {
        requireNotFrozen();
        builder.setInitialSupply(initialSupply);
        return this;
    }

    public AccountId getTreasuryAccountId() {
        return treasuryAccountId;
    }

    public TokenCreateTransaction setTreasuryAccountId(AccountId accountId) {
        requireNotFrozen();
        this.treasuryAccountId = accountId;
        return this;
    }

    public Key getAdminKey() {
        return Key.fromProtobufKey(builder.getAdminKey());
    }

    public TokenCreateTransaction setAdminKey(Key key) {
        requireNotFrozen();
        builder.setAdminKey(key.toProtobufKey());
        return this;
    }

    public Key getKycKey() {
        return Key.fromProtobufKey(builder.getKycKey());
    }

    public TokenCreateTransaction setKycKey(Key key) {
        requireNotFrozen();
        builder.setKycKey(key.toProtobufKey());
        return this;
    }

    public Key getFreezeKey() {
        return Key.fromProtobufKey(builder.getFreezeKey());
    }

    public TokenCreateTransaction setFreezeKey(Key key) {
        requireNotFrozen();
        builder.setFreezeKey(key.toProtobufKey());
        return this;
    }

    public Key getWipeKey() {
        return Key.fromProtobufKey(builder.getWipeKey());
    }

    public TokenCreateTransaction setWipeKey(Key key) {
        requireNotFrozen();
        builder.setWipeKey(key.toProtobufKey());
        return this;
    }

    public Key getSupplyKey() {
        return Key.fromProtobufKey(builder.getSupplyKey());
    }

    public TokenCreateTransaction setSupplyKey(Key key) {
        requireNotFrozen();
        builder.setSupplyKey(key.toProtobufKey());
        return this;
    }

    public boolean getFreezeDefault() {
        return builder.getFreezeDefault();
    }

    public TokenCreateTransaction setFreezeDefault(boolean freezeDefault) {
        requireNotFrozen();
        builder.setFreezeDefault(freezeDefault);
        return this;
    }

    public Instant getExpirationTime() {
        return InstantConverter.fromProtobuf(builder.getExpiry());
    }

    public TokenCreateTransaction setExpirationTime(Instant expirationTime) {
        requireNotFrozen();
        builder.clearAutoRenewPeriod();
        builder.setExpiry(InstantConverter.toProtobuf(expirationTime));
        return this;
    }

    public AccountId getAutoRenewAccountId() {
        return autoRenewAccountId;
    }

    public TokenCreateTransaction setAutoRenewAccountId(AccountId accountId) {
        requireNotFrozen();
        this.autoRenewAccountId = accountId;
        return this;
    }

    public Duration getAutoRenewPeriod() {
        return DurationConverter.fromProtobuf(builder.getAutoRenewPeriod());
    }

    public TokenCreateTransaction setAutoRenewPeriod(Duration period) {
        requireNotFrozen();
        builder.setAutoRenewPeriod(DurationConverter.toProtobuf(period));
        return this;
    }

    public String getTokenMemo() {
        return builder.getMemo();
    }

    public TokenCreateTransaction setTokenMemo(String memo) {
        requireNotFrozen();
        this.builder.setMemo(memo);
        return this;
    }

    public TokenCreateTransaction freezeWith(@Nullable Client client) {
        if (
            builder.hasAutoRenewPeriod() &&
                !builder.hasAutoRenewAccount() &&
                client != null &&
                client.getOperatorAccountId() != null
        ) {
            builder.setAutoRenewAccount(client.getOperatorAccountId().toProtobuf());
        }

        return super.freezeWith(client);
    }

    TokenCreateTransactionBody.Builder build() {
        if (treasuryAccountId != null) {
            builder.setTreasury(treasuryAccountId.toProtobuf());
        }

        if (autoRenewAccountId != null) {
            builder.setAutoRenewAccount(autoRenewAccountId.toProtobuf());
        }

        return builder;
    }

    @Override
    void validateNetworkOnIds(Client client) {
        if (treasuryAccountId != null) {
            treasuryAccountId.validate(client);
        }

        if (autoRenewAccountId != null) {
            autoRenewAccountId.validate(client);
        }
    }

    @Override
    MethodDescriptor<com.hedera.hashgraph.sdk.proto.Transaction, TransactionResponse> getMethodDescriptor() {
        return TokenServiceGrpc.getCreateTokenMethod();
    }

    @Override
    boolean onFreeze(TransactionBody.Builder bodyBuilder) {
        bodyBuilder.setTokenCreation(build());
        return true;
    }

    @Override
    void onScheduled(SchedulableTransactionBody.Builder scheduled) {
        scheduled.setTokenCreation(build());
    }
}
