package com.jugu.propertylease.device.app.task;

public interface EventTaskI<T, D> {

  void run(T event, D mgr);
}
