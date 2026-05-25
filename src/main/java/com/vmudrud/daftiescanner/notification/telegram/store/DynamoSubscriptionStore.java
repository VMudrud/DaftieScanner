package com.vmudrud.daftiescanner.notification.telegram.store;

import com.vmudrud.daftiescanner.notification.telegram.ConditionalOnTelegramEnabled;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionConflictException.Reason;
import com.vmudrud.daftiescanner.notification.telegram.store.entity.TelegramClaimItem;
import com.vmudrud.daftiescanner.notification.telegram.store.entity.TelegramSubscriptionItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactDeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactUpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnTelegramEnabled
class DynamoSubscriptionStore implements SubscriptionStore {

    private static final String CLAIM_TABLE_NAME = "daftiescanner_tg_claim";
    private static final String SUBSCRIPTION_TABLE_NAME = "daftiescanner_tg_subscription";
    private static final String COND_FAIL_CODE = "ConditionalCheckFailed";

    private final DynamoDbClient rawClient;
    private final DynamoDbEnhancedClient enhancedClient;

    private DynamoDbTable<TelegramClaimItem> claimTable;
    private DynamoDbTable<TelegramSubscriptionItem> subscriptionTable;

    // === reads ===

    @Override
    public Optional<String> chatIdByEmail(String email) {
        return Optional.ofNullable(claimTable.getItem(key(email))).map(TelegramClaimItem::getChatId);
    }

    @Override
    public Optional<String> emailByChatId(String chatId) {
        return Optional.ofNullable(subscriptionTable.getItem(key(chatId))).map(TelegramSubscriptionItem::getEmail);
    }

    // === atomic writes ===

    @Override
    public void claim(String chatId, String email) {
        long now = Instant.now().toEpochMilli();
        try {
            enhancedClient.transactWriteItems(r -> r
                    .addPutItem(claimTable, putClaimIfEmailFree(TelegramClaimItem.of(email, chatId, now)))
                    .addPutItem(subscriptionTable, putSubscriptionIfChatFree(TelegramSubscriptionItem.of(chatId, email, now))));
        } catch (TransactionCanceledException e) {
            throw firstFailureAs(e, Reason.EMAIL_ALREADY_CLAIMED, Reason.CHAT_ALREADY_SUBSCRIBED);
        }
    }

    @Override
    public void change(String chatId, String oldEmail, String newEmail) {
        long now = Instant.now().toEpochMilli();
        try {
            enhancedClient.transactWriteItems(r -> r
                    .addDeleteItem(claimTable, deleteClaimOwnedBy(oldEmail, chatId))
                    .addPutItem(claimTable, putClaimIfEmailFree(TelegramClaimItem.of(newEmail, chatId, now)))
                    .addUpdateItem(subscriptionTable, patchSubscriptionEmail(chatId, newEmail, now)));
        } catch (TransactionCanceledException e) {
            throw firstFailureAs(e, Reason.CHAT_HAS_NO_SUBSCRIPTION, Reason.EMAIL_ALREADY_CLAIMED);
        }
    }

    @Override
    public void release(String chatId, String email) {
        try {
            enhancedClient.transactWriteItems(r -> r
                    .addDeleteItem(claimTable, deleteClaimOwnedBy(email, chatId))
                    .addDeleteItem(subscriptionTable, deleteSubscription(chatId)));
        } catch (TransactionCanceledException e) {
            throw new SubscriptionConflictException(Reason.CHAT_HAS_NO_SUBSCRIPTION);
        }
    }

    // === transact-write builders (one per operation, named for what they guarantee) ===

    private TransactPutItemEnhancedRequest<TelegramClaimItem> putClaimIfEmailFree(TelegramClaimItem item) {
        return TransactPutItemEnhancedRequest.builder(TelegramClaimItem.class)
                .item(item)
                .conditionExpression(emailFree())
                .build();
    }

    private TransactPutItemEnhancedRequest<TelegramSubscriptionItem> putSubscriptionIfChatFree(TelegramSubscriptionItem item) {
        return TransactPutItemEnhancedRequest.builder(TelegramSubscriptionItem.class)
                .item(item)
                .conditionExpression(chatFree())
                .build();
    }

    private TransactDeleteItemEnhancedRequest deleteClaimOwnedBy(String email, String chatId) {
        return TransactDeleteItemEnhancedRequest.builder()
                .key(key(email))
                .conditionExpression(ownedBy(chatId))
                .build();
    }

    private TransactDeleteItemEnhancedRequest deleteSubscription(String chatId) {
        return TransactDeleteItemEnhancedRequest.builder().key(key(chatId)).build();
    }

    private TransactUpdateItemEnhancedRequest<TelegramSubscriptionItem> patchSubscriptionEmail(String chatId, String newEmail, long now) {
        return TransactUpdateItemEnhancedRequest.builder(TelegramSubscriptionItem.class)
                .item(TelegramSubscriptionItem.emailPatch(chatId, newEmail, now))
                .ignoreNullsMode(IgnoreNullsMode.SCALAR_ONLY)
                .build();
    }

    // === condition expressions ===

    private static Expression emailFree() {
        return Expression.builder().expression("attribute_not_exists(" + TelegramClaimItem.COL_EMAIL + ")").build();
    }

    private static Expression chatFree() {
        return Expression.builder().expression("attribute_not_exists(" + TelegramSubscriptionItem.COL_CHAT_ID + ")").build();
    }

    private static Expression ownedBy(String chatId) {
        return Expression.builder()
                .expression(TelegramClaimItem.COL_CHAT_ID + " = :me")
                .expressionValues(Map.of(":me", AttributeValue.fromS(chatId)))
                .build();
    }

    // === failure mapping: cancellation reasons come back in operation order, so we map by position ===

    private SubscriptionConflictException firstFailureAs(TransactionCanceledException e, Reason... reasonByPosition) {
        var reasons = e.cancellationReasons();
        for (int i = 0; i < reasons.size() && i < reasonByPosition.length; i++) {
            if (COND_FAIL_CODE.equals(reasons.get(i).code())) {
                return new SubscriptionConflictException(reasonByPosition[i]);
            }
        }
        return new SubscriptionConflictException(reasonByPosition[0]);
    }

    // === bootstrap ===

    @PostConstruct
    void init() {
        claimTable = enhancedClient.table(CLAIM_TABLE_NAME, TableSchema.fromBean(TelegramClaimItem.class));
        subscriptionTable = enhancedClient.table(SUBSCRIPTION_TABLE_NAME, TableSchema.fromBean(TelegramSubscriptionItem.class));
        ensureTable(CLAIM_TABLE_NAME, TelegramClaimItem.COL_EMAIL);
        ensureTable(SUBSCRIPTION_TABLE_NAME, TelegramSubscriptionItem.COL_CHAT_ID);
    }

    private void ensureTable(String tableName, String pkAttribute) {
        if (describeQuietly(tableName)) {
            return;
        }
        try {
            rawClient.createTable(r -> r
                    .tableName(tableName)
                    .keySchema(KeySchemaElement.builder().attributeName(pkAttribute).keyType(KeyType.HASH).build())
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName(pkAttribute).attributeType(ScalarAttributeType.S).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST));
            rawClient.waiter().waitUntilTableExists(r -> r.tableName(tableName));
        } catch (ResourceInUseException e) {
            log.error("Table {} creation conflict, likely concurrent startup: {}", tableName, e.getMessage());
        }
    }

    private boolean describeQuietly(String tableName) {
        try {
            rawClient.describeTable(r -> r.tableName(tableName));
            return true;
        } catch (ResourceNotFoundException ignored) {
            return false;
        }
    }

    private static Key key(String partitionValue) {
        return Key.builder().partitionValue(partitionValue).build();
    }
}
