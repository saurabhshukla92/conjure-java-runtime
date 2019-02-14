/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.conjure.java.okhttp;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A 'fair' queue which prioritizes by submitting thread. Requests are executed either synchronously
 * or asynchronously, more frequently synchronously. In the former case, one will block on the response;
 * in the latter case, one might submit many requests from a single thread, which if consumed in a fifo
 * fashion will potentially starve the shared resource.
 * <p>
 * Here, we hand out permits in a per-thread fifo, globally round robin fashion, so a thread which makes
 * many requests will see its requests fairly prioritized behind other threads.
 */
@NotThreadSafe
final class ThreadWorkQueue<T> {
    private final Set<Long> activeQueuedThreads = new LinkedHashSet<>();
    private final Map<Long, Queue<T>> queuedRequests = new HashMap<>();

    boolean isEmpty() {
        return activeQueuedThreads.isEmpty();
    }

    void add(T element) {
        long threadId = Thread.currentThread().getId();
        if (!activeQueuedThreads.contains(threadId)) {
            activeQueuedThreads.add(threadId);
        }
        queue(threadId).add(element);
    }

    T remove() {
        long id = nextThread();
        Queue<T> workQueue = queuedRequests.get(id);
        T result = workQueue.remove();
        if (workQueue.isEmpty()) {
            queuedRequests.remove(id);
        } else {
            activeQueuedThreads.add(id);
        }
        return result;
    }

    private long nextThread() {
        Iterator<Long> iterator = activeQueuedThreads.iterator();
        long result = iterator.next();
        iterator.remove();
        return result;
    }

    private Queue<T> queue(long id) {
        return queuedRequests.computeIfAbsent(id, key -> new ArrayDeque<>(2));
    }
}