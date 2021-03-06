/*
 * Copyright (c) 2009, OmniTI Computer Consulting, Inc.
 * All rights reserved.
 * The software in this package is published under the terms of the GPL license
 * a copy of which can be found at:
 * https://labs.omniti.com/reconnoiter/trunk/src/java/LICENSE
 */

package com.omniti.reconnoiter;

import java.lang.System;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import com.omniti.reconnoiter.MQListener;
import com.omniti.reconnoiter.broker.BrokerFactory;
import com.omniti.reconnoiter.StratconConfig;
import com.omniti.reconnoiter.StratconMessage;
import com.omniti.reconnoiter.event.*;
import com.espertech.esper.client.*;
import com.omniti.reconnoiter.esper.ExactStatViewFactory;
import com.omniti.reconnoiter.esper.DeriveViewFactory;
import com.omniti.reconnoiter.esper.CounterViewFactory;
import org.apache.log4j.BasicConfigurator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.omniti.reconnoiter.broker.IMQBroker;

class IEPEngine {
  private MQListener mql;

  public IEPEngine(StratconConfig sconf) {
    Configuration config = new Configuration();
    config.addDatabaseReference("recondb", sconf.getDBConfig());
    config.addEventTypeAutoName("com.omniti.reconnoiter.event");
    config.addPlugInView("noit", "linest", ExactStatViewFactory.class.getName());
    config.addPlugInView("noit", "derive", DeriveViewFactory.class.getName());
    config.addPlugInView("noit", "counter", CounterViewFactory.class.getName());
    config.getEngineDefaults().getThreading().setInsertIntoDispatchPreserveOrder(false);
    EPServiceProvider epService = EPServiceProviderManager.getDefaultProvider(config);

    IMQBroker broker = BrokerFactory.getBroker(sconf);
    EventHandler eh = new EventHandler(
        new ConcurrentHashMap<UUID,StratconQueryBase>(),
        epService, broker);
    mql = new MQListener(eh, broker);
    try {
      List<StratconMessage> mlist = sconf.getQueries();
      for ( StratconMessage m : mlist ) {
        mql.preprocess(m);
      }
    }
    catch (Exception e) {
      System.err.println("Failed to load queries:");
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public void start() {
    Thread listener = new Thread(mql);
    listener.start();
  }

  static public void main(String[] args) {
    BasicConfigurator.configure();
    if(args.length != 1) {
      System.err.println("Requires exactly one argument");
      return;
    }
    StratconConfig sconf = new StratconConfig(args[0]);
    (new IEPEngine(sconf)).start();

    System.err.println("IEPEngine ready...");
    BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
    while(true) {
      try {
        if(stdin.readLine() == null) throw new Exception("all done");
      } catch(Exception e) {
        System.exit(-1);
      }
    }
  }
}
