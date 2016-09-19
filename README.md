# BusEvent
模仿EventBus造轮子，目前只有极为简洁的功能且没有做优化，使用方式和EventBus类似。
EventBus地址：https://github.com/greenrobot/EventBus
##使用方法
 1. 在onCreate方法中注册 
`BusEvent.getInstance().register(this);
`
 2. 定义事件类型 
`class EventType {
	// define
}
`
 3. 定义订阅者 
`@Subscribe(threadMode = ThreadMode.ASYNC)
public void test(EventType event) {
    // handle the event
}
`
 4. 发布事件 
`BusEvent.getInstance().post(new EventType());
`
