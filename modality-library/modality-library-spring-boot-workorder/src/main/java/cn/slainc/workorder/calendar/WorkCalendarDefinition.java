package cn.slainc.workorder.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;


public record WorkCalendarDefinition(String code, String name, ZoneId timezone, Set<DayOfWeek> workDays,
                                     LocalTime workStart, LocalTime workEnd,
                                     Set<LocalDate> holidays, Set<LocalDate> specialWorkdays) {

    public WorkCalendarDefinition {
        workDays = workDays == null ? Set.of() : Set.copyOf(workDays);
        holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
        specialWorkdays = specialWorkdays == null ? Set.of() : Set.copyOf(specialWorkdays);
    }

    public boolean isWorkingDay(LocalDate date) {
        if (specialWorkdays.contains(date)) {
            return true;
        }
        if (holidays.contains(date)) {
            return false;
        }
        return workDays.contains(date.getDayOfWeek());
    }
}
