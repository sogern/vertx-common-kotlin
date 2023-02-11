package dev.sognnes.vertx.impl.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory


inline fun <reified T> getLogger(): Logger =
    LoggerFactory.getLogger(T::class.java.name)

//inline val <reified T> T.log
//    get() = LoggerFactory.getLogger(T::class.java)
