package ru.perevalov.gamerecommenderai.entity.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@WritingConverter
@Component
public class UserRoleEnumTypeConverter implements Converter<UserRole, UserRole> {
    @Override
    public UserRole convert(UserRole source) {
        return source;
    }
}
