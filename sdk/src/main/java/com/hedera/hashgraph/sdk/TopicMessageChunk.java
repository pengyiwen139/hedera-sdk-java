/*-
 *
 * Hedera Java SDK
 *
 * Copyright (C) 2020 - 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.hedera.hashgraph.sdk;

import com.hedera.hashgraph.sdk.proto.mirror.ConsensusTopicResponse;
import org.threeten.bp.Instant;

/**
 * A chunk of the topic message.
 */
final class TopicMessageChunk {
    public final Instant consensusTimestamp;
    public final long contentSize;
    public final byte[] runningHash;
    public final long sequenceNumber;

    /**
     * Create a topic message chunk from a protobuf.
     *
     * @param response                  the protobuf
     */
    TopicMessageChunk(ConsensusTopicResponse response) {
        consensusTimestamp = InstantConverter.fromProtobuf(response.getConsensusTimestamp());
        contentSize = response.getMessage().size();
        runningHash = response.getRunningHash().toByteArray();
        sequenceNumber = response.getSequenceNumber();
    }
}

