package com.jugu.propertylease.device.app.callback;

import java.util.Map;

public interface CallBackTaskI {

  void process(Map<String, String> paramMap, CallbackTaskAllocator taskMgr);
}
