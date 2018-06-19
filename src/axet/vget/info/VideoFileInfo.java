package axet.vget.info;

import java.io.File;
import java.net.URL;

import axet.wget.info.DownloadInfo;
import axet.wget.info.ProxyInfo;

public class VideoFileInfo extends DownloadInfo {

    public File targetFile;

    public VideoFileInfo(URL source) {
        super(source);
    }

    public VideoFileInfo(URL source, ProxyInfo p) {
        super(source, p);
    }

}
