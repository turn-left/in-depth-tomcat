package org.apache.catalina.valves;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * @author 【享学课堂】 King老师
 * 需要往期视频的同学加QQ：2068425757（肉兰老师）
 * 需要咨询VIP课程的同学加QQ：1011843464 （依娜老师）
 */
public class PrintIPValve extends ValveBase {
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        System.out.println("---自定义阀门PrintIPValve-----"+request.getRemoteAddr());
        getNext().invoke(request,response);//这个必须写，不写有问题
    }
}
