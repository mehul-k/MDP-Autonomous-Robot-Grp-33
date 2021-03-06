package utils;

import map.ArenaMap;
import map.MapSettings;

import java.io.*;

import static utils.IOsettings.FILE_DIR;
import static utils.IOsettings.FILE_EXT;

public class FileIO {
    public static ArenaMap loadMap(ArenaMap arenaMap, String filename) {
        arenaMap.resetMap();
        try {
            String basePath = new File("").getAbsolutePath();
            BufferedReader buf;
            InputStream inputStream;
            try {
                inputStream = new FileInputStream(basePath + '/' + FILE_DIR + filename + FILE_EXT);
                buf = new BufferedReader(new InputStreamReader(inputStream));
            } catch (IOException e) {
                System.out.println("Try reading file again...");
                inputStream = new FileInputStream(basePath + "/Algorithms/" + FILE_DIR + filename + FILE_EXT);
                buf = new BufferedReader(new InputStreamReader(inputStream));
            }
            String line = buf.readLine();
            StringBuilder sb = new StringBuilder();
            while (line != null) {
                sb.append(line);
                line = buf.readLine();
            }

            String bin = sb.toString();
            int binPtr = 0;
            for (int row = MapSettings.MAP_ROWS - 1; row >= 0; row--) {
                for (int col = 0; col < MapSettings.MAP_COLS; col++) {
                    if (bin.charAt(binPtr) == '1') {
                        arenaMap.getCell(row, col).setObstacle(true);
                    }
                    binPtr++;
                }
            }
            arenaMap.setAllExplored();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return arenaMap;
    }
}
