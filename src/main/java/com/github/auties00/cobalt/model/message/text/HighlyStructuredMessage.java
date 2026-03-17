package com.github.auties00.cobalt.model.message.text;

import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.interactive.TemplateMessage;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

@ProtobufMessage(name = "Message.HighlyStructuredMessage")
public final class HighlyStructuredMessage implements TemplateMessage.Title, Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String namespace;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String elementName;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    List<String> params;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String fallbackLg;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String fallbackLc;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    List<HSMLocalizableParameter> localizableParams;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String deterministicLg;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String deterministicLc;

    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    TemplateMessage hydratedHsm;


    HighlyStructuredMessage(String namespace, String elementName, List<String> params, String fallbackLg, String fallbackLc, List<HSMLocalizableParameter> localizableParams, String deterministicLg, String deterministicLc, TemplateMessage hydratedHsm) {
        this.namespace = namespace;
        this.elementName = elementName;
        this.params = params;
        this.fallbackLg = fallbackLg;
        this.fallbackLc = fallbackLc;
        this.localizableParams = localizableParams;
        this.deterministicLg = deterministicLg;
        this.deterministicLc = deterministicLc;
        this.hydratedHsm = hydratedHsm;
    }

    public Optional<String> namespace() {
        return Optional.ofNullable(namespace);
    }

    public Optional<String> elementName() {
        return Optional.ofNullable(elementName);
    }

    public List<String> params() {
        return params == null ? List.of() : Collections.unmodifiableList(params);
    }

    public Optional<String> fallbackLg() {
        return Optional.ofNullable(fallbackLg);
    }

    public Optional<String> fallbackLc() {
        return Optional.ofNullable(fallbackLc);
    }

    public List<HSMLocalizableParameter> localizableParams() {
        return localizableParams == null ? List.of() : Collections.unmodifiableList(localizableParams);
    }

    public Optional<String> deterministicLg() {
        return Optional.ofNullable(deterministicLg);
    }

    public Optional<String> deterministicLc() {
        return Optional.ofNullable(deterministicLc);
    }

    public Optional<TemplateMessage> hydratedHsm() {
        return Optional.ofNullable(hydratedHsm);
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public void setParams(List<String> params) {
        this.params = params;
    }

    public void setFallbackLg(String fallbackLg) {
        this.fallbackLg = fallbackLg;
    }

    public void setFallbackLc(String fallbackLc) {
        this.fallbackLc = fallbackLc;
    }

    public void setLocalizableParams(List<HSMLocalizableParameter> localizableParams) {
        this.localizableParams = localizableParams;
    }

    public void setDeterministicLg(String deterministicLg) {
        this.deterministicLg = deterministicLg;
    }

    public void setDeterministicLc(String deterministicLc) {
        this.deterministicLc = deterministicLc;
    }

    public void setHydratedHsm(TemplateMessage hydratedHsm) {
        this.hydratedHsm = hydratedHsm;
    }

    public sealed interface ParamOneof permits HSMLocalizableParameter.HSMCurrency, HSMLocalizableParameter.HSMDateTime {
    }

    @ProtobufMessage(name = "Message.HighlyStructuredMessage.HSMLocalizableParameter")
    public static final class HSMLocalizableParameter {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String defaultValue;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        HSMLocalizableParameter.HSMCurrency currency;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        HSMLocalizableParameter.HSMDateTime dateTime;


        HSMLocalizableParameter(String defaultValue, HSMCurrency currency, HSMDateTime dateTime) {
            this.defaultValue = defaultValue;
            this.currency = currency;
            this.dateTime = dateTime;
        }

        public Optional<String> defaultValue() {
            return Optional.ofNullable(defaultValue);
        }

        public Optional<? extends ParamOneof> paramOneof() {
            if (currency != null) return Optional.of(currency);
            if (dateTime != null) return Optional.of(dateTime);
            return Optional.empty();
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
    }

        public void setCurrency(HSMCurrency currency) {
            this.currency = currency;
    }

        public void setDateTime(HSMDateTime dateTime) {
            this.dateTime = dateTime;
    }

        public sealed interface DatetimeOneof permits HSMDateTime.HSMDateTimeComponent, HSMDateTime.HSMDateTimeUnixEpoch {
        }

        @ProtobufMessage(name = "Message.HighlyStructuredMessage.HSMLocalizableParameter.HSMCurrency")
        public static final class HSMCurrency implements ParamOneof {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String currencyCode;

            @ProtobufProperty(index = 2, type = ProtobufType.INT64)
            Long amount1000;


            HSMCurrency(String currencyCode, Long amount1000) {
                this.currencyCode = currencyCode;
                this.amount1000 = amount1000;
            }

            public Optional<String> currencyCode() {
                return Optional.ofNullable(currencyCode);
            }

            public OptionalLong amount1000() {
                return amount1000 == null ? OptionalLong.empty() : OptionalLong.of(amount1000);
            }

            public void setCurrencyCode(String currencyCode) {
                this.currencyCode = currencyCode;
    }

            public void setAmount1000(Long amount1000) {
                this.amount1000 = amount1000;
    }
        }

        @ProtobufMessage(name = "Message.HighlyStructuredMessage.HSMLocalizableParameter.HSMDateTime")
        public static final class HSMDateTime implements ParamOneof {
            @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
            HSMLocalizableParameter.HSMDateTime.HSMDateTimeComponent component;

            @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
            HSMLocalizableParameter.HSMDateTime.HSMDateTimeUnixEpoch unixEpoch;


            HSMDateTime(HSMDateTimeComponent component, HSMDateTimeUnixEpoch unixEpoch) {
                this.component = component;
                this.unixEpoch = unixEpoch;
            }

            public Optional<? extends DatetimeOneof> datetimeOneof() {
                if (component != null) return Optional.of(component);
                if (unixEpoch != null) return Optional.of(unixEpoch);
                return Optional.empty();
            }

            public void setComponent(HSMDateTimeComponent component) {
                this.component = component;
    }

            public void setUnixEpoch(HSMDateTimeUnixEpoch unixEpoch) {
                this.unixEpoch = unixEpoch;
    }

            @ProtobufMessage(name = "Message.HighlyStructuredMessage.HSMLocalizableParameter.HSMDateTime.HSMDateTimeComponent")
            public static final class HSMDateTimeComponent implements DatetimeOneof {
                @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
                HSMLocalizableParameter.HSMDateTime.HSMDateTimeComponent.DayOfWeekType dayOfWeek;

                @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
                Integer year;

                @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
                Integer month;

                @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
                Integer dayOfMonth;

                @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
                Integer hour;

                @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
                Integer minute;

                @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
                HSMLocalizableParameter.HSMDateTime.HSMDateTimeComponent.CalendarType calendar;


                HSMDateTimeComponent(DayOfWeekType dayOfWeek, Integer year, Integer month, Integer dayOfMonth, Integer hour, Integer minute, CalendarType calendar) {
                    this.dayOfWeek = dayOfWeek;
                    this.year = year;
                    this.month = month;
                    this.dayOfMonth = dayOfMonth;
                    this.hour = hour;
                    this.minute = minute;
                    this.calendar = calendar;
                }

                public Optional<DayOfWeekType> dayOfWeek() {
                    return Optional.ofNullable(dayOfWeek);
                }

                public OptionalInt year() {
                    return year == null ? OptionalInt.empty() : OptionalInt.of(year);
                }

                public OptionalInt month() {
                    return month == null ? OptionalInt.empty() : OptionalInt.of(month);
                }

                public OptionalInt dayOfMonth() {
                    return dayOfMonth == null ? OptionalInt.empty() : OptionalInt.of(dayOfMonth);
                }

                public OptionalInt hour() {
                    return hour == null ? OptionalInt.empty() : OptionalInt.of(hour);
                }

                public OptionalInt minute() {
                    return minute == null ? OptionalInt.empty() : OptionalInt.of(minute);
                }

                public Optional<CalendarType> calendar() {
                    return Optional.ofNullable(calendar);
                }

                public void setDayOfWeek(DayOfWeekType dayOfWeek) {
                    this.dayOfWeek = dayOfWeek;
    }

                public void setYear(Integer year) {
                    this.year = year;
    }

                public void setMonth(Integer month) {
                    this.month = month;
    }

                public void setDayOfMonth(Integer dayOfMonth) {
                    this.dayOfMonth = dayOfMonth;
    }

                public void setHour(Integer hour) {
                    this.hour = hour;
    }

                public void setMinute(Integer minute) {
                    this.minute = minute;
    }

                public void setCalendar(CalendarType calendar) {
                    this.calendar = calendar;
    }

                @ProtobufEnum(name = "Message.HighlyStructuredMessage.HSMLocalizableParameter.HSMDateTime.HSMDateTimeComponent.CalendarType")
                public static enum CalendarType {
                    GREGORIAN(1),
                    SOLAR_HIJRI(2);

                    CalendarType(@ProtobufEnumIndex int index) {
                        this.index = index;
                    }

                    final int index;

                    public int index() {
                        return this.index;
                    }
                }

                @ProtobufEnum(name = "Message.HighlyStructuredMessage.HSMLocalizableParameter.HSMDateTime.HSMDateTimeComponent.DayOfWeekType")
                public static enum DayOfWeekType {
                    MONDAY(1),
                    TUESDAY(2),
                    WEDNESDAY(3),
                    THURSDAY(4),
                    FRIDAY(5),
                    SATURDAY(6),
                    SUNDAY(7);

                    DayOfWeekType(@ProtobufEnumIndex int index) {
                        this.index = index;
                    }

                    final int index;

                    public int index() {
                        return this.index;
                    }
                }
            }

            @ProtobufMessage(name = "Message.HighlyStructuredMessage.HSMLocalizableParameter.HSMDateTime.HSMDateTimeUnixEpoch")
            public static final class HSMDateTimeUnixEpoch implements DatetimeOneof {
                @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
                Instant timestamp;


                HSMDateTimeUnixEpoch(Instant timestamp) {
                    this.timestamp = timestamp;
                }

                public Optional<Instant> timestamp() {
                    return Optional.ofNullable(timestamp);
                }

                public void setTimestamp(Instant timestamp) {
                    this.timestamp = timestamp;
    }
            }
        }
    }
}
