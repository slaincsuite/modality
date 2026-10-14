package cn.slainc.core.base;

import cn.slainc.core.page.PageParam;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseEntityTest {

    static class SampleEntity extends BaseEntity {
    }

    @Test
    void equalsAndHashCodeByIdentity() {
        SampleEntity left = new SampleEntity();
        SampleEntity right = new SampleEntity();
        assertNotEquals(left, right);

        left.setId(1L);
        right.setId(1L);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());

        right.setId(2L);
        assertNotEquals(left, right);
        assertEquals(left, left);
        assertFalse(left.equals(null));
        assertFalse(left.equals("not-an-entity"));
    }

    @Test
    void auditAndTenantFields() {
        SampleEntity entity = new SampleEntity();
        entity.setId(9L);
        entity.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        entity.setUpdatedAt(LocalDateTime.of(2024, 1, 2, 0, 0));
        entity.setCreatedBy("admin");
        entity.setUpdatedBy("admin");
        entity.setDeleted(0);
        entity.setVersion(1);
        entity.setTenantId(1L);

        assertEquals(9L, (long) entity.getId());
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0), entity.getCreatedAt());
        assertEquals("admin", entity.getCreatedBy());
        assertEquals("admin", entity.getUpdatedBy());
        assertEquals(0, (int) entity.getDeleted());
        assertEquals(1, (int) entity.getVersion());
        assertEquals(1L, (long) entity.getTenantId());
    }

    @Test
    void baseQueryDefaultsAndConversion() {
        BaseQuery query = new BaseQuery();
        assertEquals(1L, query.getPageNum());
        assertEquals(PageParam.DEFAULT_SIZE, query.getPageSize());
        assertEquals("ASC", query.getOrderDirection());
        assertEquals(1L, query.toPageParam().current());
    }

    @Test
    void dtoAndVoAreSerializableMarkers() {
        assertTrue(new BaseDTO() instanceof java.io.Serializable);
        assertTrue(new BaseVO() instanceof java.io.Serializable);
    }
}
