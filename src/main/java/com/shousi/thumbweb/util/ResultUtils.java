package com.shousi.thumbweb.util;

import com.shousi.thumbweb.common.BaseResponse;
import com.shousi.thumbweb.common.ErrorCode;

public class ResultUtils {
 
     /**
      * 成功
      *
      */
     public static <T> BaseResponse<T> success(T data) {
         return new BaseResponse<>(0, data, "ok");
     }
 
 
     /**
      * 失败
      *
      */
     public static BaseResponse<?> error(ErrorCode errorCode) {
         return new BaseResponse<>(errorCode);
     }
 
     /**
      * 失败
      *
      */
     public static BaseResponse<?> error(ErrorCode errorCode, String message) {
         return new BaseResponse<>(errorCode.getCode(), null, message);
     }
 
 }