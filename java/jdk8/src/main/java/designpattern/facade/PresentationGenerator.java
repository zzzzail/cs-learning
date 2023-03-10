package designpattern.facade;

/**
 * 生成表现层的模块
 *
 * @author zail
 * @date 2022/5/26
 */
public class PresentationGenerator {
    
    public void generate() {
        // 1. 从配置管理里获取相应的配置信息
        ConfigModel config = ConfigManager.getInstance().getConfigData();
        // 2. 按照配置文件生成相应的代码，保存成文件
        if (config.isNeedGenPresentation()) {
            System.out.println("生成表现层");
        }
    }
}
