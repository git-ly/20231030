package org.example.util.http;

import org.example.api.exceptions.BadRequestException;
import org.example.api.exceptions.InvalidInputException;
import org.example.api.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GlobalControllerExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ServerWebInputException.class)
    public @ResponseBody HttpErrorInfo handleExceptions(ServerHttpRequest request,ServerWebInputException ex){
        return createHttpErrorInfo(HttpStatus.BAD_REQUEST,request,new Exception(ex.getReason(),ex));
    }
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    public @ResponseBody HttpErrorInfo handleBadRequestExceptions(
            ServerHttpRequest request,BadRequestException ex
    ){
        return createHttpErrorInfo(HttpStatus.BAD_REQUEST,request,ex);
    }
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundException.class)
    public @ResponseBody HttpErrorInfo handleNotFoundExceptions(
            ServerHttpRequest request, NotFoundException ex
    ){
        return createHttpErrorInfo(HttpStatus.NOT_FOUND,request,ex);
    }
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ExceptionHandler(InvalidInputException.class)
    public @ResponseBody HttpErrorInfo handleInvalidInputException(
            ServerHttpRequest request,InvalidInputException ex
    ){
        return createHttpErrorInfo(HttpStatus.UNPROCESSABLE_ENTITY,request,ex);
    }
    private HttpErrorInfo createHttpErrorInfo(HttpStatus httpStatus,
                                              ServerHttpRequest request,
                                              Exception ex){
        final String path = request.getPath().pathWithinApplication().value();
        final String message = ex.getMessage();
        LOG.debug("Returning HTTP status: {} for path: {}, message: {}",httpStatus,
                path,message);
        return new HttpErrorInfo(httpStatus,path,message);
    }
}
