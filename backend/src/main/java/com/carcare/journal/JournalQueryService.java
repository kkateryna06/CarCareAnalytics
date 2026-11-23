package com.carcare.journal;

import com.carcare.exception.ApiException;
import com.carcare.vehicle.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.carcare.journal.JournalDtos.*;
import static com.carcare.journal.JournalTypes.*;

@Service @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class JournalQueryService {
    private final JournalService journal;
    private final VehicleRepository vehicles;
    private final JdbcTemplate jdbc;
    public JournalQueryService(JournalService journal, VehicleRepository vehicles, JdbcTemplate jdbc) { this.journal=journal; this.vehicles=vehicles; this.jdbc=jdbc; }
    public JournalPage<Object> list(long userId,long vehicleId,Kind kind,int page,int size,String sort,LocalDate from,LocalDate to,Category category,DocumentType type) {
        own(userId,vehicleId); String direction=validate(page,size,sort,from,to);
        String table=switch(kind) { case ODOMETER -> "odometer_entries"; case REFUELING -> "refuelings"; case EXPENSE -> "expenses"; case SERVICE -> "service_records"; case DOCUMENT -> "document_deadlines"; };
        String date=kind==Kind.DOCUMENT?"valid_until":"date";
        var args=new ArrayList<Object>(); args.add(vehicleId);
        String where=" WHERE vehicle_id=?" + dates(date,from,to,args);
        if(category!=null) { if(kind!=Kind.EXPENSE) badFilter(); where+=" AND category=?"; args.add(category.name()); }
        if(type!=null) { if(kind!=Kind.DOCUMENT) badFilter(); where+=" AND type=?"; args.add(type.name()); }
        long total=jdbc.queryForObject("SELECT count(*) FROM " + table + where,Long.class,args.toArray());
        var paged=new ArrayList<>(args); paged.add(size); paged.add((long)page*size);
        List<Long> ids=jdbc.queryForList("SELECT id FROM " + table + where + " ORDER BY " + date + " " + direction + ", id " + direction + " LIMIT ? OFFSET ?",Long.class,paged.toArray());
        List<Object> content=ids.stream().map(id -> journal.response(journal.repository(kind).findById(id).orElseThrow(ApiException::notFound))).toList();
        return new JournalPage<>(content,page,size,total,pages(total,size));
    }
    private static final String TIMELINE="""
        (SELECT id,vehicle_id,'ODOMETER'::varchar AS kind,date,coalesce(note,'Пробег') AS title,NULL::numeric AS amount_pln,odometer_km,NULL::varchar AS category,created_at
         FROM odometer_entries WHERE source_type='MANUAL'
         UNION ALL
         SELECT r.id,r.vehicle_id,'REFUELING',r.date,coalesce(r.station,'Заправка'),e.amount_pln,r.odometer_km,'FUEL',r.created_at FROM refuelings r JOIN expenses e ON e.id=r.expense_id
         UNION ALL
         SELECT id,vehicle_id,'EXPENSE',date,title,amount_pln,NULL,category,created_at FROM expenses WHERE source_type='MANUAL'
         UNION ALL
         SELECT s.id,s.vehicle_id,'SERVICE',s.date,s.description,e.amount_pln,s.odometer_km,'SERVICE',s.created_at FROM service_records s LEFT JOIN expenses e ON e.id=s.expense_id) journal
        """;
    public JournalPage<TimelineItem> timeline(long userId,long vehicleId,int page,int size,String sort,LocalDate from,LocalDate to,Kind kind,Category category) {
        return timeline(userId,vehicleId,page,size,sort,from,to,kind,category,false);
    }
    public JournalPage<TimelineItem> timeline(long userId,long vehicleId,int page,int size,String sort,LocalDate from,LocalDate to,Kind kind,Category category,boolean expensesOnly) {
        own(userId,vehicleId); String direction=validate(page,size,sort,from,to);
        var args=new ArrayList<Object>(); args.add(vehicleId);
        String where=" WHERE vehicle_id=?"+dates("date",from,to,args);
        if(kind!=null) { if(kind==Kind.DOCUMENT) badFilter(); where+=" AND kind=?"; args.add(kind.name()); }
        if(category!=null) { where+=" AND category=?"; args.add(category.name()); }
        if(expensesOnly) where+=" AND amount_pln IS NOT NULL";
        long total=jdbc.queryForObject("SELECT count(*) FROM "+TIMELINE+where,Long.class,args.toArray());
        args.add(size); args.add((long)page*size);
        var content=jdbc.query("SELECT * FROM "+TIMELINE+where+" ORDER BY date "+direction+",created_at "+direction+",kind,id "+direction+" LIMIT ? OFFSET ?", (rs,row)->new TimelineItem(rs.getLong("id"),Kind.valueOf(rs.getString("kind")),rs.getObject("date",LocalDate.class),rs.getString("title"),rs.getBigDecimal("amount_pln"),rs.getObject("odometer_km",Integer.class),rs.getString("category")==null?null:Category.valueOf(rs.getString("category")),rs.getTimestamp("created_at").toInstant()),args.toArray());
        return new JournalPage<>(content,page,size,total,pages(total,size));
    }
    private void own(long userId,long vehicleId) { vehicles.findByIdAndOwnerId(vehicleId,userId).orElseThrow(ApiException::notFound); }
    private String dates(String column,LocalDate from,LocalDate to,List<Object> args) {
        String sql=""; if(from!=null) { sql+=" AND "+column+">=?"; args.add(from); } if(to!=null) { sql+=" AND "+column+"<=?"; args.add(to); } return sql;
    }
    private String validate(int page,int size,String sort,LocalDate from,LocalDate to) {
        if(page<0 || size<1 || size>100 || !Set.of("date,asc","date,desc").contains(sort)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION","page >= 0, size от 1 до 100, sort=date,asc или date,desc");
        if(from!=null && to!=null && from.isAfter(to)) throw ApiException.invalid(Map.of("dateTo","Конец периода не может быть раньше начала"));
        return sort.endsWith("asc")?"ASC":"DESC";
    }
    private int pages(long total,int size) { return (int)((total+size-1)/size); }
    private void badFilter() { throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","Фильтр не применим к этому разделу"); }
}
