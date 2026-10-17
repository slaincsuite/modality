package cn.slainc.modules.infra;

public final class InfraKeys {

    public static final String CONFIG_HISTORY_TABLE = "t_infra_config_history";
    public static final String DICT_TYPE_TABLE = "t_infra_dict_type";
    public static final String DICT_ITEM_TABLE = "t_infra_dict_item";
    public static final String NOTICE_TABLE = "t_infra_notice";
    public static final String NOTIFY_TEMPLATE_TABLE = "t_infra_notify_template";
    public static final String NOTIFY_MESSAGE_TABLE = "t_infra_notify_message";
    public static final String NOTIFY_WEBHOOK_TABLE = "t_infra_notify_webhook";
    public static final String NOTIFY_RECORD_TABLE = "t_infra_notify_record";
    public static final String NOTIFY_RECEIVER_TABLE = "t_infra_notify_receiver";
    public static final String NOTIFY_SUBSCRIPTION_TABLE = "t_infra_notify_subscription";
    public static final String NOTIFY_PREFERENCE_TABLE = "t_infra_notify_preference";
    public static final String FILE_RECORD_TABLE = "t_infra_file_record";

    public static final String API_KEY_TABLE = "t_infra_api_key";
    public static final String API_KEY_ROTATION_TABLE = "t_infra_api_key_rotation";
    public static final String API_KEY_METRIC_TABLE = "t_infra_api_key_metric";

    public static final String SEARCH_DOCUMENT_TABLE = "t_search_document";
    public static final String SEARCH_INDEX_TERM_TABLE = "t_search_index_term";
    public static final String SEARCH_HISTORY_TABLE = "t_search_history";
    public static final String RECYCLE_ITEM_TABLE = "t_recycle_item";

    public static final String PERMISSION_CONFIG_QUERY = "infra:config:query";
    public static final String PERMISSION_CONFIG_CREATE = "infra:config:create";
    public static final String PERMISSION_CONFIG_UPDATE = "infra:config:update";
    public static final String PERMISSION_CONFIG_DELETE = "infra:config:delete";
    public static final String PERMISSION_CONFIG_ROLLBACK = "infra:config:rollback";
    public static final String PERMISSION_CONFIG_TRANSFER = "infra:config:transfer";
    public static final String PERMISSION_DICT_QUERY = "infra:dict:query";
    public static final String PERMISSION_DICT_CREATE = "infra:dict:create";
    public static final String PERMISSION_DICT_UPDATE = "infra:dict:update";
    public static final String PERMISSION_DICT_DELETE = "infra:dict:delete";
    public static final String PERMISSION_DICT_TRANSFER = "infra:dict:transfer";
    public static final String PERMISSION_NOTICE_QUERY = "infra:notice:query";
    public static final String PERMISSION_NOTICE_CREATE = "infra:notice:create";
    public static final String PERMISSION_NOTICE_UPDATE = "infra:notice:update";
    public static final String PERMISSION_NOTICE_DELETE = "infra:notice:delete";
    public static final String PERMISSION_NOTIFY_QUERY = "infra:notify:query";
    public static final String PERMISSION_NOTIFY_CREATE = "infra:notify:create";
    public static final String PERMISSION_NOTIFY_UPDATE = "infra:notify:update";
    public static final String PERMISSION_NOTIFY_DELETE = "infra:notify:delete";
    public static final String PERMISSION_NOTIFY_SEND = "infra:notify:send";
    public static final String PERMISSION_NOTIFY_RETRY = "infra:notify:retry";
    public static final String PERMISSION_FILE_QUERY = "infra:file:query";
    public static final String PERMISSION_FILE_UPLOAD = "infra:file:upload";
    public static final String PERMISSION_FILE_DELETE = "infra:file:delete";
    public static final String PERMISSION_FILE_MANAGE = "infra:file:manage";
    public static final String PERMISSION_REDIS_QUERY = "infra:redis:query";
    public static final String PERMISSION_REDIS_DELETE = "infra:redis:delete";
    public static final String PERMISSION_SQL_QUERY = "infra:sql:query";
    public static final String PERMISSION_LOG_QUERY = "infra:log:query";
    public static final String PERMISSION_LOG_LEVEL = "infra:log:level";
    public static final String PERMISSION_CACHE_QUERY = "infra:cache:query";
    public static final String PERMISSION_CACHE_CLEAR = "infra:cache:clear";
    public static final String PERMISSION_APIKEY_QUERY = "infra:apikey:query";
    public static final String PERMISSION_APIKEY_ISSUE = "infra:apikey:issue";
    public static final String PERMISSION_APIKEY_MANAGE = "infra:apikey:manage";
    public static final String PERMISSION_SYSTEM_QUERY = "infra:system:query";
    public static final String PERMISSION_SYSTEM_ALERT = "infra:system:alert";
    public static final String PERMISSION_SEARCH_QUERY = "infra:search:query";
    public static final String PERMISSION_SEARCH_INDEX = "infra:search:index";
    public static final String PERMISSION_SEARCH_REMOVE = "infra:search:remove";
    public static final String PERMISSION_RECYCLE_QUERY = "infra:recycle:query";
    public static final String PERMISSION_RECYCLE_RESTORE = "infra:recycle:restore";
    public static final String PERMISSION_RECYCLE_PURGE = "infra:recycle:purge";

    public static final String RECYCLE_TYPE_NOTICE = "INFRA_NOTICE";

    public static final String MASKED_VALUE = "******";
    public static final String DEFAULT_NOTIFY_TOPIC = "infra-notify";

    private InfraKeys() {
    }
}
