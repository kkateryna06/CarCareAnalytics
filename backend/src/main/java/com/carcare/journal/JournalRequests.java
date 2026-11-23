package com.carcare.journal;

import com.carcare.exception.ApiException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.carcare.journal.JournalTypes.*;
import static com.carcare.journal.JournalDtos.*;

@Component
public class JournalRequests {
    private final ObjectMapper json;
    private final Validator validator;
    public JournalRequests(ObjectMapper json, Validator validator) { this.json=json; this.validator=validator; }
    public Object decode(Kind kind, JsonNode body, Object previous) {
        if (body==null || !body.isObject() || body.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"BAD_REQUEST","Укажите поля записи");
        Class<?> type=switch(kind) { case ODOMETER -> OdometerWrite.class; case REFUELING -> RefuelingWrite.class; case EXPENSE -> ExpenseWrite.class; case SERVICE -> ServiceWrite.class; case DOCUMENT -> DocumentWrite.class; };
        ObjectNode values=previous==null ? json.createObjectNode() : json.valueToTree(previous);
        Set<String> allowed=new HashSet<>();
        for (var component:type.getRecordComponents()) allowed.add(component.getName());
        body.fields().forEachRemaining(field -> {
            if (!allowed.contains(field.getKey())) throw new ApiException(HttpStatus.BAD_REQUEST,"UNKNOWN_FIELD","Неизвестное поле: " + field.getKey());
            values.set(field.getKey(),field.getValue());
        });
        Object result;
        try { result=json.treeToValue(values,type); }
        catch (Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"BAD_REQUEST","Некорректный формат поля"); }
        var fields=new TreeMap<String,String>();
        validator.validate(result).forEach(v -> fields.put(v.getPropertyPath().toString(),v.getMessage()));
        if (result instanceof ExpenseWrite e && e.category()==Category.FUEL) fields.put("category","Заправка создаёт расход FUEL автоматически");
        if (result instanceof ServiceWrite s) {
            if (s.nextDueDate()!=null && s.date()!=null && s.nextDueDate().isBefore(s.date())) fields.put("nextDueDate","Следующий срок не может быть раньше обслуживания");
            if (s.nextDueOdometerKm()!=null && s.odometerKm()!=null && s.nextDueOdometerKm()<s.odometerKm()) fields.put("nextDueOdometerKm","Следующий пробег не может быть меньше пробега обслуживания");
        }
        if (!fields.isEmpty()) throw ApiException.invalid(fields);
        return result;
    }
    public JsonNode tree(Object value) { return json.valueToTree(value); }
}
