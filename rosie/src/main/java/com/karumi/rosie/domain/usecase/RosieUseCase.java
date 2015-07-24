/*
 * The MIT License (MIT) Copyright (c) 2014 karumi Permission is hereby granted, free of charge,
 * to any person obtaining a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to
  * do so, subject to the following conditions: The above copyright notice and this permission
  * notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE
  * IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
  * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
  * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.karumi.rosie.domain.usecase;

import com.karumi.rosie.domain.usecase.annotation.Success;
import com.karumi.rosie.domain.usecase.callback.CallbackScheduler;
import com.karumi.rosie.domain.usecase.callback.MainThreadCallbackScheduler;
import com.karumi.rosie.domain.usecase.callback.OnSuccessCallback;
import com.karumi.rosie.domain.usecase.error.ErrorNotHandledException;
import com.karumi.rosie.domain.usecase.error.OnErrorCallback;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * This is the base implementation of a UseCase. Every UseCase implementation has to extend from
 * this class.
 */
public class RosieUseCase {

  private WeakReference<OnSuccessCallback> onSuccessCallback;
  private WeakReference<OnErrorCallback> onErrorCallback;

  private CallbackScheduler callbackScheduler;

  public void setCallbackScheduler(CallbackScheduler callbackScheduler) {
    validateCallbackScheduler(callbackScheduler);
    this.callbackScheduler = callbackScheduler;
  }

  /**
   * Notify to the callback onSuccessCallback that something it's work fine. You can invoke the
   *
   * method as
   * many times as you want. You only need on your onSuccessCallback a method with the same
   * arguments.
   *
   * @param values that will be send to the onSuccessCallback callback. Note: By default this
   * method
   * return the response to the UI Thread.
   */
  protected void notifySuccess(Object... values) {
    Method[] methodsArray = onSuccessCallback.get().getClass().getMethods();
    if (methodsArray.length > 0) {
      Method methodToInvoke =
          UseCaseFilter.filterValidMethodArgs(values, methodsArray, Success.class);
      invokeMethodInTheCallbackScheduler(methodToInvoke, values);
    } else {
      throw new IllegalStateException(
          "The OnSuccessCallback instance configured has no methods annotated with the "
              + "@Success annotation.");
    }
  }

  /**
   * Notify to the error listener that an error happened, if you don't declare an specific error
   * handler for you use case, this error will be manage for the generic error system.
   *
   * @param error the error to send to the callback.
   * @throws ErrorNotHandledException this exception launch when the specific error is not
   * handled. You don't need manage this exception UseCaseHandler do it for you.
   */

  protected void notifyError(final Error error) throws ErrorNotHandledException {
    if (onErrorCallback == null) {
      throw new ErrorNotHandledException(error);
    }
    final OnErrorCallback callback = this.onErrorCallback.get();
    if (callback != null) {
      try {
        getCallbackScheduler().post(new Runnable() {
          @Override public void run() {
            callback.onError(error);
          }
        });
      } catch (IllegalArgumentException e) {
        throw new ErrorNotHandledException(error);
      }
    } else {
      throw new ErrorNotHandledException(error);
    }
  }

  /**
   * The OnSuccessCallback passed as argument in this method will be referenced as a
   * WeakReference inside RosieUseCase and UseCaseParams to avoid memory leaks during the
   * Activity lifecycle pause-destroy stage. Remember to keep a strong reference of your
   * OnSuccessCallback instance if needed.
   */
  void setOnSuccessCallback(OnSuccessCallback onSuccessCallback) {
    if (onSuccessCallback != null) {
      this.onSuccessCallback = new WeakReference<>(onSuccessCallback);
    }
  }

  /**
   * The OnErrorCallback passed as argument in this method will be referenced as a
   * WeakReference inside RosieUseCase and UseCaseParams to avoid memory leaks during the
   * Activity lifecycle pause-destroy stage. Remember to keep a strong reference of your
   * OnErrorCallback instance if needed.
   */

  void setOnErrorCallback(OnErrorCallback onErrorCallback) {
    if (onErrorCallback != null) {
      this.onErrorCallback = new WeakReference<>(onErrorCallback);
    }
  }

  private void invokeMethodInTheCallbackScheduler(final Method methodToInvoke,
      final Object[] values) {
    if (onSuccessCallback != null) {
      OnSuccessCallback callback = onSuccessCallback.get();
      if (callback != null) {
        getCallbackScheduler().post(new Runnable() {
          @Override public void run() {
            try {
              methodToInvoke.invoke(onSuccessCallback.get(), values);
            } catch (Exception e) {
              throw new RuntimeException("Internal error invoking the success object", e);
            }
          }
        });
      }
    }
  }

  private CallbackScheduler getCallbackScheduler() {
    if (callbackScheduler == null) {
      callbackScheduler = new MainThreadCallbackScheduler();
    }
    return callbackScheduler;
  }

  private void validateCallbackScheduler(CallbackScheduler callbackScheduler) {
    if (callbackScheduler == null) {
      throw new IllegalArgumentException("You can't use a null instance as CallbackScheduler.");
    }
  }
}