package com.wulinpeng.busevent;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class BusEvent {

    /**
     * 用于切换到主线程调用方法
     */
    private Handler mHandler;

    private static BusEvent instance;

    /**
     * Event类型参数作为key，保存对应的所有SubscribeMethod
     */
    private Map<Class, CopyOnWriteArrayList<SubscribeMethod>> methodsByType = new HashMap<>();

    /**
     * 每个线程对应
     */
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

    /**
     * 遍历所有方法，找出有Subsceibe注解的方法，保存
     */
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

    /**
     * 只保存参数为1个的方法，通过注解获得执行线程
     * @param method
     * @param a
     * @param subscriber
     */
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

    /**
     * 遍历所有方法，删除有Subscribe注解的方法
     * @param subscriber
     */
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

    /**
     * 通过方法的参数得到所有该参数的SubscribeMethod，然后比较Subscriber，对应的删除
     * @param method
     * @param subscriber
     */
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

    /**
     * 发送事件
     * @param event
     */
    public void post(Object event) {
        // 得到本线程对应的PostingThread，将事件加入queue，然后判断本线程的状态，如果isPosting就返回
        PostingThread p = mPostingThread.get();
        p.eventQueue.add(event);
        if (p.isPosting) {
            return;
        }
        // 开始调用方法，通过postEvent
        p.isPosting = true;
        while (!p.eventQueue.isEmpty()) {
            Object e = p.eventQueue.remove(0);
            postEvent(e);
        }
        // 队列为空，调用完毕，设为false
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
