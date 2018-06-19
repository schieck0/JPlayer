package axet.vget.vhs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import axet.vget.info.VideoFileInfo;
import axet.vget.info.VideoInfo;
import axet.vget.vhs.YouTubeInfo.StreamCombined;
import axet.vget.vhs.YouTubeInfo.YoutubeQuality;
import axet.wget.info.DownloadInfo;
import axet.wget.info.ex.DownloadError;
import axet.wget.info.ex.DownloadRetry;

public class YouTubeQParser extends YouTubeParser {

    YoutubeQuality q;

    public YouTubeQParser(YoutubeQuality q) {
        this.q = q;
    }

    public List<VideoFileInfo> extract(final VideoInfo vinfo, final AtomicBoolean stop, final Runnable notify) {
        List<VideoDownload> sNextVideoURL = extractLinks((YouTubeInfo) vinfo, stop, notify);

        if (sNextVideoURL.size() == 0) {
            // rare error:
            //
            // The live recording you're trying to play is still being processed
            // and will be available soon. Sorry, please try again later.
            //
            // retry. since youtube may already rendrered propertly quality.
            throw new DownloadRetry("empty video download list," + " wait until youtube will process the video");
        }

        Collections.sort(sNextVideoURL, new VideoContentFirst());

        for (int i = 0; i < sNextVideoURL.size(); i++) {
            VideoDownload v = sNextVideoURL.get(i);

            boolean found = true;

            StreamCombined vq = (StreamCombined) v.stream;

            found &= q.equals(vq.vq);

            if (found) {
                YouTubeInfo yinfo = (YouTubeInfo) vinfo;
                yinfo.setStreamInfo(vq);
                VideoFileInfo info = new VideoFileInfo(v.url);
                vinfo.setInfo(Arrays.asList(info));
                vinfo.setSource(v.url);
                return vinfo.getInfo();
            }
        }

        // throw download stop if user choice not maximum quality and we have no
        // video rendered by youtube

        throw new DownloadError("no video user quality found");
    }

}
