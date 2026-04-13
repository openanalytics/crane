/*
 * Crane
 *
 * Copyright (C) 2021-2026 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.crane.util;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceFactory {

    public static ExecutorService create(String name, int corePoolSize, int maximumPoolSize) {
        ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern(name + "-%d").build();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 60L, TimeUnit.SECONDS, new SynchronousQueue(), threadFactory);
        executor.prestartAllCoreThreads();
        return executor;
    }

    public static ExecutorService create(String name) {
        return create(name, 16, Integer.MAX_VALUE);
    }

    public static ExecutorService createWithLinkedBlockingQueue(String name, int corePoolSize, int maximumPoolSize) {
        ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern(name + "-%d").build();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory);
        executor.prestartAllCoreThreads();
        return executor;
    }

}
