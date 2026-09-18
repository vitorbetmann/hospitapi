package com.vitorbetmann.hospitapi.scheduling.api;

import com.vitorbetmann.hospitapi.scheduling.service.AppointmentNotFoundException;
import com.vitorbetmann.hospitapi.scheduling.service.InvalidAppointmentException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        return switch (ex) {
            case AppointmentNotFoundException e -> error(env, ErrorType.NOT_FOUND, e.getMessage());
            case InvalidAppointmentException e -> error(env, ErrorType.BAD_REQUEST, e.getMessage());
            default -> null;
        };
    }

    private static GraphQLError error(DataFetchingEnvironment env, ErrorType type, String message) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(message)
                .build();
    }
}