package house.hunt;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class LoadConfigFile {
	
	public static Properties getConfigProperties(String path) throws IOException {
		Properties props = new Properties();
		props.load(new FileInputStream(path));
		return props;
	}
}
