package pers.clare.session.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

@SuppressWarnings("unused")
public class JsonUtil {

    private JsonUtil(){}

    /**
     * 反序列化
     * 1.反序列化時，忽略對象不存在的參數
     *
     * @param json the value
     * @return the string
     */
    public static <T> T decode(String json, TypeReference<T> valueTypeRef)
            throws  IOException {
        if (json == null) return null;
        return create().readValue(json, valueTypeRef);
    }

    /**
     * 反序列化
     * 1.反序列化時，忽略對象不存在的參數
     *
     * @param json the value
     * @return the string
     */
    public static <T> T decode(String json, Class<T> type)
            throws IOException {
        if (json == null) return null;
        return create().readValue(json, type);
    }


    /**
     * 序列化
     * 1.避免Map null key 的問題無法序列化
     *
     * @param value the value
     * @return the string
     */
    public static String encode(Object value) throws JsonProcessingException {
        return create().writeValueAsString(value);
    }

    /**
     * 序列化
     * 1.避免Map null key 的問題無法序列化
     * 2.限制String Value 最大長度
     *
     * @param stringMaxLength string max length
     * @param value           the value
     * @return the string
     */
    public static String encode(int stringMaxLength, Object value) throws JsonProcessingException {
        return create(stringMaxLength).writeValueAsString(value);
    }

    /**
     * 建立ObjectMapper
     * 1.避免Map null key 的問題無法序列化
     * 2.限制String Value 最大長度
     */
    public static ObjectMapper create(int stringMaxLength) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(String.class, new StringLimitJsonSerializer(stringMaxLength));
        ObjectMapper om = create();
        om.registerModule(module);
        return om;
    }

    /**
     * 建立ObjectMapper
     * 1.反序列化時，忽略對象類型不存在的參數
     * 2.避免Map null key 的問題無法序列化
     */
    public static ObjectMapper create() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.getSerializerProvider().setNullKeySerializer(new NullKeyJsonSerializer());
        om.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return om;
    }

    /**
     * 限制字串長度
     */
    static class StringLimitJsonSerializer extends JsonSerializer<String> {
        private final int limit;

        StringLimitJsonSerializer(int limit) {
            this.limit = limit;
        }

        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value.length() > limit) {
                value = value.substring(0, limit - 3) + "...";
            }
            gen.writeString(value);
        }
    }

    static class NullKeyJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeFieldName("null");
        }
    }

    public static class RewriteTypeReference<T> extends TypeReference<T> {
        public static final Field field;

        static {
            Field field1;
            try {
                field1 = TypeReference.class.getDeclaredField("_type");
                field1.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                field1 = null;
            }
            field = field1;
        }

        protected RewriteTypeReference(Type type) {
            super();
            try {
                field.set(this, type);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
