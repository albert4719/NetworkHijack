## NetworkHijack 
![57a4f07663c06837495ccb859b971f98](https://github.com/user-attachments/assets/9ec59193-4ada-410b-9105-47e79d75b92a)

bukkit插件,拦截并重放其他插件所有http请求响应体 \
指令/nh start 插件名称,开始记录此插件发送的http请求,控制台可看到输出 \
指令/nh stop 插件名称，停止记录 \
如果插件在start之前就已经发送了请求，可以使用plugman重载此插件(有概率无法捕获请求),如果无法捕获直接重启服务器即可,会自动记录上次start的插件 \
记录到的请求url以及响应体会存放到本插件文件夹的data目录中，下次该插件发起对应url的请求时，会进行拦截并重放上次记录的响应体，让此插件以为请求成功
