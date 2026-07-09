package top.littlewin.aicoder.exception;

import lombok.Data;

@Data
public class BusinessException extends RuntimeException{
    private final int code;

    public BusinessException(int code, String msg){
        super(msg);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public  BusinessException(ErrorCode errorCode, String msg){
        super(msg);
        this.code = errorCode.getCode();
    }
}
