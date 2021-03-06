/******************************************************************************
 * e-voting system                                                            *
 * Copyright (C) 2016 DSX Technologies Limited.                               *
 * *
 * This program is free software; you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation; either version 2 of the License, or          *
 * (at your option) any later version.                                        *
 * *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied                         *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * *
 * You can find copy of the GNU General Public License in LICENSE.txt file    *
 * at the top-level directory of this distribution.                           *
 * *
 * Removal or modification of this copyright notice is prohibited.            *
 * *
 ******************************************************************************/

package uk.dsxt.voting.common.domain.nodes;

import lombok.extern.log4j.Log4j2;
import uk.dsxt.voting.common.domain.dataModel.*;
import uk.dsxt.voting.common.messaging.MessagesSerializer;
import uk.dsxt.voting.common.utils.CollectionsHelper;
import uk.dsxt.voting.common.utils.InternalLogicException;
import uk.dsxt.voting.common.utils.crypto.CryptoHelper;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
public class VotingOrganizer implements NetworkClient {

    private NetworkMessagesSender network;

    private final ScheduledExecutorService calculateResultsService;

    private final PrivateKey privateKey;

    private final CryptoHelper cryptoHelper;

    private final MessagesSerializer messagesSerializer;

    private final PublicKey publicKey;

    private final long calculateResultsDelay;

    private static class MessageRecord {
        VoteResult result;
        String resultDigest;
        List<VoteStatus> statuses = new ArrayList<>();
    }

    private static class VotingRecord {
        Voting voting;
        Map<String, MessageRecord> resultsByMessageId = new HashMap<>();
    }

    private final Map<String, VotingRecord> votingsById = new HashMap<>();

    public VotingOrganizer(MessagesSerializer messagesSerializer, CryptoHelper cryptoProvider, Map<String, PublicKey> participantKeysById, PrivateKey privateKey, long calculateResultsDelay)
        throws InternalLogicException, GeneralSecurityException {
        calculateResultsService = Executors.newScheduledThreadPool(10);
        this.messagesSerializer = messagesSerializer;
        this.privateKey = privateKey;
        this.cryptoHelper = cryptoProvider;
        this.calculateResultsDelay = calculateResultsDelay;
        publicKey = participantKeysById.get(MasterNode.MASTER_HOLDER_ID);
    }

    @Override
    public void setNetworkMessagesSender(NetworkMessagesSender networkMessagesSender) {
        network = networkMessagesSender;
    }

    public void addNewVoting(Voting voting) {
        try {
            network.addVoting(voting);
        } catch (InternalLogicException e) {
            log.error("addNewVoting. addVoting failed. votingId={} error={}", voting.getId(), e.getMessage());
        }
        calculateResultsService.schedule(() -> calculateResults(voting.getId()), Math.max(voting.getEndTimestamp() - System.currentTimeMillis(), 0) + calculateResultsDelay, TimeUnit.MILLISECONDS);
        log.info("addNewVoting. Voting added. votingId={}", voting.getId());
    }

    private void calculateResults(String votingId) {
        log.info("calculateResults started. votingId={}", votingId);
        VotingRecord votingRecord;
        synchronized (votingsById) {
            votingRecord = votingsById.get(votingId);
        }
        if (votingRecord == null) {
            log.warn("calculateResults. Voting not found {}", votingId);
            return;
        }
        VoteResult totalResult = new VoteResult(votingId, null);
        synchronized (votingRecord) {
            for (Map.Entry<String, MessageRecord> resultEntry : votingRecord.resultsByMessageId.entrySet()) {
                String messageId = resultEntry.getKey();
                MessageRecord messageRecord = resultEntry.getValue();
                synchronized (messageRecord) {
                    if (messageRecord.result == null) {
                        log.warn("calculateResults. messageRecord without result. messageId={}", messageId);
                        continue;
                    }
                    VoteResult result = messageRecord.result;
                    if (messageRecord.statuses.size() == 0) {
                        log.warn("calculateResults. messageRecord without status. messageId={} ownerId={}", messageId, result.getHolderId());
                        continue;
                    } else if (messageRecord.statuses.size() > 1) {
                        log.warn("calculateResults. messageRecord with {} statuses. messageId={} ownerId={}", messageRecord.statuses.size(), messageId, result.getHolderId());
                    }
                    for (VoteStatus status : messageRecord.statuses) {
                        if (status.getStatus() != VoteResultStatus.OK) {
                            log.info("calculateResults. Skip result due its VoteStatus {}. messageId={} ownerId={}", status.getStatus(), messageId, result.getHolderId());
                            continue;
                        }
                        String error = result.findError(votingRecord.voting);
                        if (error != null) {
                            log.warn("calculateResults. Skip incorrect result. messageId={} ownerId={} error={}", status.getStatus(), messageId, result.getHolderId(), error);
                            continue;
                        }
                        try {
                            if (!cryptoHelper.verifySignature(messageRecord.resultDigest, status.getVoteSign(), publicKey)) {
                                log.error("calculateResults. VoteStatus with incorrect signature. messageId={} ownerId={}", messageId, result.getHolderId());
                                continue;
                            }
                        } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                            log.error("calculateResults. VoteStatus verify signature failed. messageId={} ownerId={} error={}", messageId, result.getHolderId(), e.getMessage());
                            continue;
                        }
                        if (messageRecord.resultDigest == null || !messageRecord.resultDigest.equals(status.getVoteDigest())) {
                            log.error("calculateResults. VoteStatus vote digest is incorrect. messageId={} ownerId={}", messageId, result.getHolderId());
                            continue;
                        }
                        totalResult.add(result);
                        log.debug("calculateResults. votingId={}. Add vote from {} packetSize={} sumSize={}", votingId, result.getHolderId(), result.getPacketSize(), totalResult.getPacketSize());
                        break;
                    }
                }
            }
        }
        try {
            network.addVotingTotalResult(totalResult, votingRecord.voting);
            log.info("calculateResults. totalResult={}", totalResult);
        } catch (InternalLogicException e) {
            log.info("calculateResults. addVotingTotalResult failed. totalResult={}", totalResult, e);
        }
    }

    @Override
    public void addVoting(Voting voting) {
        VotingRecord votingRecord = CollectionsHelper.synchronizedGetOrAdd(votingsById, voting.getId(), VotingRecord::new);
        synchronized (votingRecord) {
            votingRecord.voting = voting;
        }
    }

    @Override
    public void addVotingTotalResult(VoteResult result) {
    }

    @Override
    public void addVoteStatus(VoteStatus status, String messageId, boolean isCommitted, boolean isSelf) {
        VotingRecord votingRecord = CollectionsHelper.synchronizedGetOrAdd(votingsById, status.getVotingId(), VotingRecord::new);
        synchronized (votingRecord) {
            MessageRecord messageRecord = CollectionsHelper.synchronizedGetOrAdd(votingRecord.resultsByMessageId, status.getMessageId(), MessageRecord::new);
            synchronized (messageRecord) {
                messageRecord.statuses.add(status);
            }
        }
    }

    @Override
    public void addVoteToMaster(VoteResult result, String messageId, String serializedResult, boolean isCommitted, boolean isSelf) {
        VotingRecord votingRecord = CollectionsHelper.synchronizedGetOrAdd(votingsById, result.getVotingId(), VotingRecord::new);
        String digest = null;
        try {
            digest = cryptoHelper.getDigest(serializedResult);
        } catch (NoSuchAlgorithmException e) {
            log.error("addVote. getDigest failed: {}", e.getMessage());
        }
        synchronized (votingRecord) {
            MessageRecord messageRecord = CollectionsHelper.synchronizedGetOrAdd(votingRecord.resultsByMessageId, messageId, MessageRecord::new);
            synchronized (messageRecord) {
                messageRecord.result = result;
                messageRecord.resultDigest = digest;
            }
        }
    }

    @Override
    public void notifyVote(String messageId, boolean isCommitted, boolean isSelf) {
    }
}
