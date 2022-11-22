package com.notessensei.interceptortest;

import java.time.LocalDateTime;
import java.util.UUID;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

public class MainVerticle extends AbstractVerticle {

  private static final String SEND_TIME = "sendTime";

  public static void main(String[] args) {
    MainVerticle mv = new MainVerticle();
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(mv);
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    ReflectorVerticle rv = new ReflectorVerticle();
    vertx.deployVerticle(rv)
        .compose(v -> this.deployInterceptor())
        .compose(v -> this.deployHttp())
        .onSuccess(v -> startPromise.complete())
        .onFailure(startPromise::fail);
  }

  private Future<Void> deployInterceptor() {
    vertx.eventBus().addInboundInterceptor(getInterceptorHandler("INBOUND"));
    vertx.eventBus().addOutboundInterceptor(getInterceptorHandler("OUTBOUND"));

    return Future.succeededFuture();
  }

  private Handler<DeliveryContext<JsonObject>> getInterceptorHandler(final String direction) {
    return (ctx) -> {
      final JsonObject body = (JsonObject) ctx.body();
      if (body.containsKey(ReflectorVerticle.REPLY_TIME)) {
        System.out.printf("%s Message reply: %s at %s %s%n",
            direction,
            body.getString(ReflectorVerticle.MANUAL_ID),
            body.getString(ReflectorVerticle.REPLY_TIME),
            body.getString("url"));
      } else {
        System.out.printf("%s Message sent: %s at %s %s%n",
            direction,
            body.getString(ReflectorVerticle.MANUAL_ID),
            body.getString(MainVerticle.SEND_TIME),
            body.getString("url"));
      }
      ctx.next();
    };
  }

  private Future<Void> deployHttp() {
    final Promise<Void> promise = Promise.promise();
    vertx.createHttpServer().requestHandler(this::requestHandler)
        .listen(8888)
        .onSuccess(v -> {
          System.out.println("HTTP server started on port 8888");
          promise.complete();
        })
        .onFailure(promise::fail);
    return promise.future();
  }

  private void requestHandler(final HttpServerRequest request) {
    final String tempAdr = UUID.randomUUID().toString();
    final String timeStamp = LocalDateTime.now().toString();

    final MessageConsumer<JsonObject> mc = vertx.eventBus().consumer(tempAdr);

    mc.handler(returnMSG -> {
      JsonObject returned = returnMSG.body();
      request.response()
          .setStatusCode(200)
          .putHeader("content-type", "application/json")
          .end(returned.toBuffer());
      mc.unregister();
      System.out.println("---");
    });

    final DeliveryOptions options = new DeliveryOptions();
    final JsonObject message = new JsonObject()
        .put("url", request.absoluteURI())
        .put("method", request.method().toString())
        .put(SEND_TIME, timeStamp)
        .put(ReflectorVerticle.MANUAL_ID, tempAdr + "-" + timeStamp);
    options.addHeader(ReflectorVerticle.REPLY_TO, tempAdr);
    vertx.eventBus().send(ReflectorVerticle.REFLECTOR_ADR, message, options);
  }
}
