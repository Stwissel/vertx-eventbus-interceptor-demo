package com.notessensei.interceptortest;

import java.time.LocalDateTime;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class ReflectorVerticle extends AbstractVerticle {

  public static final String REFLECTOR_ADR = "send.this.to.me";
  public static final String REPLY_TO = "replyto";
  public static final String REPLY_TIME = "replyTime";
  public static final String MANUAL_ID = "manualId";

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    this.getVertx().eventBus().consumer(REFLECTOR_ADR, this::doReflection);
    startPromise.complete();
  }

  private void doReflection(final Message<JsonObject> message) {
    final MultiMap headers = message.headers();
    final String replyTo = headers.get(REPLY_TO);
    final JsonObject body = message.body();
    vertx.setTimer(1000, handler -> {
      body.put(REPLY_TIME, LocalDateTime.now().toString());
      vertx.eventBus().send(replyTo, body);
    });
  }
}
