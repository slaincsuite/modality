package cn.slainc.modules.workorder.service.order;

import cn.slainc.modules.workorder.WorkorderModuleApplication;
import cn.slainc.modules.workorder.dto.WorkOrderTypeView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(classes = WorkorderModuleApplication.class)
class WorkOrderTypeServiceTest {

    @Autowired
    private WorkOrderTypeService typeService;

    @Test
    void listsRegisteredTypesWithEnabledFlag() {
        List<WorkOrderTypeView> types = typeService.list();
        assertThat(types).extracting(WorkOrderTypeView::code)
                .contains("generic", "approval", "fault");
        WorkOrderTypeView generic = types.stream()
                .filter(type -> "generic".equals(type.code()))
                .findFirst()
                .orElseThrow();
        assertThat(generic.name()).isEqualTo("通用工单");
        assertThat(generic.description()).isNotBlank();
        assertThat(generic.enabled()).isTrue();
    }
}
