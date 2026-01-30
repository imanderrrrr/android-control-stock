package com.are.distribuidora.core.result

// Resultado de operaciones orientado a TDD y fácil de asertar.
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Error(val failure: Failure) : Result<Nothing>()

    inline fun onSuccess(block: (T) -> Unit): Result<T> {
        if (this is Success) block(value)
        return this
    }

    inline fun onError(block: (Failure) -> Unit): Result<T> {
        if (this is Error) block(failure)
        return this
    }

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
}
