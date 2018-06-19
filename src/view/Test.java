package view;

import axet.vget.VGet;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class Test {

    public static void main(String[] args) {
        try {
            VGet v = new VGet(new URL("https://www.youtube.com/watch?v=GbzKr46VvD0"), new File("c:/teste"));

            System.out.println(v.getVideo().getWeb());
//            v.download();
            System.out.println("finish");
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
    }
}
