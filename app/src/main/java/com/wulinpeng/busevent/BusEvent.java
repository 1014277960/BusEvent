package com.wulinpeng.busevent;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by wulinpeng on 16/9/19.
 */
public class BusEvent {

    private Handler mHandler;

    private static BusEvent instance;

    private Map<Class, CopyOnWriteArrayList<SubscribeMethod>> methodsByType = new HashMap<>();

    private ThreadLocal<PostingThread> mPostingThread = new ThreadLocal<PostingThread>() {
        @Override
        public PostingThread get() {
            return new PostingThread();
        }
    };

    private BusEvent() {
        mHandler = new Handler(Looper.getMainLooper());
        Log.d("Debug", "BusEvent thread" + Thread.currentThread());
    }

    /**
     * 单例返回一个实例
     * @return
     */
    public static BusEvent getInstance() {
        if (instance == null) {
            synchronized (BusEvent.class) {
                if (instance == null) {
                    instance = new BusEvent();
                }
            }
        }
        return instance;
    }

    public void register(Object subscriber) {
        Class c = subscriber.getClass();
        Method[] methods = c.getDeclaredMethods();

        for (Method method: methods) {
            Annotation[] annotations = method.getAnnotations();
            for (Annotation a: annotations) {
                if (a instanceof Subscribe) {
                    addMethod(method, (Subscribe) a, subscriber);
                }
            }
        }
    }

    private void addMethod(Method method, Subscribe a, Object subscriber) {
        Class<?>[] types = method.getParameterTypes();
        if (types.length == 1) {
            CopyOnWriteArrayList<SubscribeMethod> m = null;
            ThreadMode mode = a.threadMode();
            Class<?> eventType = types[0];
            synchronized (this) {
                if (methodsByType.containsKey(eventType)) {
                    m = methodsByType.get(eventType);
                } else {
                    m = new CopyOnWriteArrayList<>();
                    methodsByType.put(eventType, m);
                }
            }
            m.add(new SubscribeMethod(method, mode, subscriber));
        }
    }

    public void unRegister(Object subscriber) {
        Class c = subscriber.getClass();
        Method[] methods = c.getDeclaredMethods();

        for (Method method: methods) {
            Annotation[] annotations = method.getAnnotations();
            for (Annotation a: annotations) {
                if (a instanceof Subscribe) {
                    removeMethod(method, subscriber);
                }
            }
        }
    }

    private void removeMethod(Method method, Object subscriber) {
        Class<?>[] types = method.getParameterTypes();
        if (types.length == 1) {
            Class<?> type = types[0];
            CopyOnWriteArrayList<SubscribeMethod> m = methodsByType.get(type);
            List<SubscribeMethod> removes = new ArrayList<>();
            for (int i = 0; i != m.size(); i++) {
                SubscribeMethod sm = m.get(i);
                if (sm.method.equals(method) && sm.subscriber.equals(subscriber)) {
                    removes.add(sm);
                }
            }
            for (SubscribeMethod s: removes) {
                m.remove(s);
            }
        }
    }

    public void post(Object event) {
        PostingThread p = mPostingThread.get();
        p.eventQueue.add(event);
        if (p.isPosting) {
            return;
        }
        p.isPosting = true;
        while (!p.eventQueue.isEmpty()) {
            Object e = p.eventQueue.remove(0);
            postEvent(e);
        }
        p.isPosting = false;
    }

    private void postEvent(final Object event) {
        CopyOnWriteArrayList<SubscribeMethod> subscribeMethods = null;
        synchronized (this) {
            subscribeMethods = methodsByType.get(event.getClass());
        }
        for (final SubscribeMethod subscribeMethod: subscribeMethods) {
            ThreadMode mode = subscribeMethod.mode;
            if (mode == ThreadMode.MAIN) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        invokeMethod(event, subscribeMethod);
                    }
                });
            } else if (mode == ThreadMode.POSTING) {
                invokeMethod(event, subscribeMethod);
            } else {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        invokeMethod(event, subscribeMethod);
                        return null;
                    }
                }.execute();
            }
        }
    }

    private void invokeMethod(Object event, SubscribeMethod subscribeMethod) {
        try {
            subscribeMethod.method.invoke(subscribeMethod.subscriber, event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

enum ThreadMode{
    MAIN, POSTING, ASYNC
}

class PostingThread {
    List<Object> eventQueue = new ArrayList<>();
    boolean isPosting;
}

class SubscribeMethod {

    public Method method;

    public ThreadMode mode;

    public Object subscriber;

    public SubscribeMethod(Method method, ThreadMode mode, Object subscriber) {
        this.method = method;
        this.mode = mode;
        this.subscriber = subscriber;
    }
}
