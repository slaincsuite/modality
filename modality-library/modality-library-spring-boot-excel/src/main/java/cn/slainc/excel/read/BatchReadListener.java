package cn.slainc.excel.read;

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class BatchReadListener<T> implements ReadListener<T> {

    private final ReadOptions options;
    private final Validator validator;
    private final Consumer<List<T>> batchConsumer;
    private final ImportResult<T> result = new ImportResult<>();
    private final List<T> buffer = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public BatchReadListener(ReadOptions options, Validator validator, Consumer<List<T>> batchConsumer) {
        this.options = options == null ? ReadOptions.defaults() : options;
        this.validator = validator;
        this.batchConsumer = batchConsumer;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        int rowIndex = resolveRowIndex(context);
        result.increaseTotalRows();
        if (options.stopOnError() && !result.getErrors().isEmpty()) {
            return;
        }
        List<ImportError> rowErrors = validate(data, rowIndex);
        if (!rowErrors.isEmpty()) {
            rowErrors.forEach(result::addError);
            if (options.stopOnError()) {
                result.markAborted();
            }
            return;
        }
        buffer.add(data);
        result.addRecord(data);
        if (buffer.size() >= options.batchSize()) {
            flush();
        }
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) {
        String rootMessage = cn.slainc.core.exception.ExceptionUtils.getMessage(exception);
        result.addError(ImportError.of(resolveRowIndex(context), null, null,
                rootMessage == null || rootMessage.isBlank() ? "数据解析失败" : rootMessage));
        if (options.stopOnError()) {
            result.markAborted();
            throw new IllegalStateException("第 " + resolveRowIndex(context) + " 行解析失败，已终止导入", exception);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
    }

    public ImportResult<T> getResult() {
        return result;
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        if (batchConsumer != null) {
            batchConsumer.accept(List.copyOf(buffer));
        }
        buffer.clear();
    }

    private List<ImportError> validate(T data, int rowIndex) {
        List<ImportError> errors = new ArrayList<>();
        if (data == null) {
            errors.add(ImportError.of(rowIndex, null, null, "数据为空"));
            return errors;
        }
        if (validator == null) {
            return errors;
        }
        Set<ConstraintViolation<T>> violations = validator.validate(data);
        for (ConstraintViolation<T> violation : violations) {
            errors.add(ImportError.of(rowIndex, violation.getPropertyPath().toString(),
                    violation.getInvalidValue(), violation.getMessage()));
        }
        return errors;
    }

    private int resolveRowIndex(AnalysisContext context) {
        if (context == null || context.readRowHolder() == null) {
            return -1;
        }
        Integer rowIndex = context.readRowHolder().getRowIndex();
        return rowIndex == null ? -1 : rowIndex + 1;
    }
}
