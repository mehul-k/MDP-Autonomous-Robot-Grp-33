package logic.exploration;

import hardware.Agent;
import hardware.AgentSettings;
import hardware.AgentSettings.Actions;
import hardware.AgentSettings.Direction;
import logic.fastestpath.AStarHeuristicSearch;
import map.ArenaMap;
import map.Cell;
import map.MapSettings;
import network.NetworkMgr;
import utils.SimulatorSettings;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Queue;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import static utils.MsgParsingUtils.parseFastestPathString;
import static utils.SimulatorSettings.GOHOMESLOW_SLEEP;
import static utils.SimulatorSettings.SIM;

abstract public class ExplorationAlgo {
    protected static ArenaMap exploredArenaMap;
    protected static ArenaMap realArenaMap;
    protected static Agent bot;
    protected int coverageLimit = 300;
    protected int timeLimit = 3600;    // in second
    protected int areaExplored;
    protected long startTime; // in millisecond
    protected long currentTime;
    private boolean calibrationMode;
    private int lastCalibrate;

    ArrayList<Actions> actionsTaken = new ArrayList<>();

    private static String goToPointActionsString;


    Scanner scanner = new Scanner(System.in);

    public ExplorationAlgo(ArenaMap exploredArenaMap, ArenaMap realArenaMap, Agent bot, int coverageLimit, int timeLimit) {
        this.exploredArenaMap = exploredArenaMap;
        this.realArenaMap = realArenaMap;
        this.bot = bot;
        this.coverageLimit = coverageLimit;
        this.timeLimit = timeLimit * 1000;

        System.out.println("[coverageLimit && timeLimit(s)] " + coverageLimit + " | " + timeLimit);
    }

    public void runExploration() throws InterruptedException {
        if (!bot.isSim()) {
            System.out.println("Starting calibration...");

            if (!bot.isSim()) {
                // Facing NORTH
                turnBotDirection(Direction.WEST);
                moveBot(Actions.ALIGN_FRONT);
                turnBotDirection(Direction.SOUTH);
                moveBot(Actions.ALIGN_FRONT);
                turnBotDirection(Direction.EAST);
            }

            while (true) {
                System.out.println("Waiting for ES|...");
                String msg = NetworkMgr.getInstance().receiveMsg();
//                String[] msgArr = msg.split("\\|");
                if (msg.equals(NetworkMgr.EXP_START)) break;
            }
        }

        // Explore
        System.out.println("Starting exploration...");

        // prepare for timing
        startTime = System.currentTimeMillis();

        areaExplored = calculateAreaExplored();
        System.out.println("Starting state - area explored: " + areaExplored);
        System.out.println();

        explorationLoop(bot.getAgtY(), bot.getAgtX());
        if (!bot.isSim()) NetworkMgr.getInstance().sendMsg("EF|", NetworkMgr.INSTRUCTIONS);
        alignBeforeFastestPath();


//        if (!bot.isSim()) {
//            NetworkMgr.getInstance().sendMsg(null, NetworkMgr.BOT_START);
//        }
//        senseAndRepaint();
        exploredArenaMap.repaint();
    }


    abstract protected void nextMove();

    /**
     * Loops through robot movements until one (or more) of the following conditions is met:
     * 1. Robot is back at (r, c)
     * 2. areaExplored > coverageLimit
     * 3. System.currentTimeMillis() > endTime
     */
    protected void explorationLoop(int r, int c) {
        System.out.println("[coverageLimit + timeLimit] " + coverageLimit + " | " + timeLimit);

        long elapsedTime = 0;
        do {
            NetworkMgr.getInstance().sendMsg("Z", NetworkMgr.INSTRUCTIONS);
            senseAndRepaint();
            nextMove();
            System.out.printf("Current Bot Pos: [%d, %d]\n", bot.getAgtX(), bot.getAgtY());

            areaExplored = calculateAreaExplored();
            System.out.println("Area explored: " + areaExplored);
            System.out.println();

            if (bot.getAgtY() == r && bot.getAgtX() == c) {
                if (areaExplored >= 100) {
                    System.out.println("Exploration finished in advance!");
                    break;
                }
            }
            elapsedTime = getElapsedTime();
//            scanner.nextLine();
            System.out.println("[doWhile loop elapsed time] " + getElapsedTime());
        } while (areaExplored <= coverageLimit && elapsedTime < timeLimit);

        if (areaExplored == 300) {
//            System.out.println("[explorationLoop()] goHome()");
            goHome();
        } else if ((areaExplored >= coverageLimit && areaExplored < 300) || (elapsedTime >= timeLimit && areaExplored < 300)) {
            // Exceed coverage or time limit

            elapsedTime = getElapsedTime();
            if (areaExplored >= coverageLimit) System.out.printf("Reached coverage limit, successfully explored %d grids\n", areaExplored);
            if (elapsedTime >= timeLimit) System.out.printf("Reached time limit, exploration has taken %d millisecond(ms)\n", elapsedTime);

            if (SIM) {
                System.out.println("Arena not fully explored, goHomeSlow() option can be chosen, enter \"Y\" or \"y\" to continue: ");
                String userInput = scanner.nextLine();
                if (userInput.toLowerCase().equals("y")) {
                    try {
                        System.out.println("[explorationLoop()] Agent sleeping for " + GOHOMESLOW_SLEEP/1000 + " second(s) before executing goHomeSlow()");
                        TimeUnit.MILLISECONDS.sleep(GOHOMESLOW_SLEEP);
                    } catch (InterruptedException e) {
                        System.out.println("[explorationLoop()] Sleeping interruption exception");
                    }
                    goHomeSlow();
                } else {
                    System.out.println("goHomeSlow() option not chosen, robot will be stationary.");
                }
            } else {
                goHomeSlow();
            }

        } else {
            // have unexplored cells, visit surrounding cells of those unvisited cells
            goHome();

            System.out.printf("Current Bot Pos: [%d, %d]\n", bot.getAgtX(), bot.getAgtY());

            AStarHeuristicSearch keepExploring;
            ArrayList<Cell> unExploredCells;
            Cell destCell;
            unExploredCells = findUnexplored();
            int i = 0;
            while (!unExploredCells.isEmpty()) {
                int targetRow, targetCol;
                System.out.println("Unexplored cells: " + unExploredCells.size());
                Cell targetCell = unExploredCells.get(i);
                targetRow = targetCell.getRow();
                targetCol = targetCell.getCol();
                destCell = findSurroundingReachable(targetRow, targetCol);
                if (destCell != null) {
                    System.out.println(destCell);
                    keepExploring = new AStarHeuristicSearch(exploredArenaMap, bot, realArenaMap);
                    keepExploring.runFastestPath(destCell.getRow(), destCell.getCol());
                    i = 0;
                } else {
                    i++;
                }

                elapsedTime = getElapsedTime();
                if (areaExplored >= coverageLimit) {
                    System.out.printf("Reached coverage limit, successfully explored %d grids\n", areaExplored);
                    break;
                }
                if (elapsedTime >= timeLimit) {
                    System.out.printf("Reached time limit, exploration has taken %d millisecond(ms)\n", elapsedTime);
                    break;
                }

                if (i == 0) unExploredCells = findUnexplored();
            }
            goHome();
        }
        System.out.println("Exploration Completed!");
    }

    /**
     * Returns true if the right side of the robot is free to move into.
     */
    protected boolean lookRight() {
//        System.out.println("[DEBUG: Function executed] lookRight()");
        switch (bot.getAgtDir()) {
            case NORTH:
                return eastFree();
            case EAST:
                return southFree();
            case SOUTH:
                return westFree();
            case WEST:
                return northFree();
        }
        return false;
    }

    /**
     * Returns true if the robot is free to move forward.
     */
    protected boolean lookForward() {
//        System.out.println("[DEBUG: Function executed] lookForward()");
        switch (bot.getAgtDir()) {
            case NORTH:
                return northFree();
            case EAST:
                return eastFree();
            case SOUTH:
                return southFree();
            case WEST:
                return westFree();
        }
        return false;
    }

    /**
     * * Returns true if the left side of the robot is free to move into.
     */
    protected boolean lookLeft() {
//        System.out.println("[DEBUG: Function executed] lookLeft()");
        switch (bot.getAgtDir()) {
            case NORTH:
                return westFree();
            case EAST:
                return northFree();
            case SOUTH:
                return eastFree();
            case WEST:
                return southFree();
        }
        return false;
    }

    /**
     * Returns true if the robot can move to the north cell.
     */
    protected boolean northFree() {
//        System.out.println("[DEBUG: Function executed] northFree()");
        int botRow = bot.getAgtY();
        int botCol = bot.getAgtX();
        return (isExploredNotObstacle(botRow + 1, botCol - 1) && isExploredAndFree(botRow + 1, botCol) && isExploredNotObstacle(botRow + 1, botCol + 1));
    }

    /**
     * Returns true if the robot can move to the east cell.
     */
    protected boolean eastFree() {
//        System.out.println("[DEBUG: Function executed] eastFree()");
        int botRow = bot.getAgtY();
        int botCol = bot.getAgtX();
        return (isExploredNotObstacle(botRow - 1, botCol + 1) && isExploredAndFree(botRow, botCol + 1) && isExploredNotObstacle(botRow + 1, botCol + 1));
    }

    /**
     * Returns true if the robot can move to the south cell.
     */
    protected boolean southFree() {
//        System.out.println("[DEBUG: Function executed] southFree()");
        int botRow = bot.getAgtY();
        int botCol = bot.getAgtX();
        return (isExploredNotObstacle(botRow - 1, botCol - 1) && isExploredAndFree(botRow - 1, botCol) && isExploredNotObstacle(botRow - 1, botCol + 1));
    }

    /**
     * Returns true if the robot can move to the west cell.
     */
    protected boolean westFree() {
//        System.out.println("[DEBUG: Function executed] westFree()");
        int botRow = bot.getAgtY();
        int botCol = bot.getAgtX();
        return (isExploredNotObstacle(botRow - 1, botCol - 1) && isExploredAndFree(botRow, botCol - 1) && isExploredNotObstacle(botRow + 1, botCol - 1));
    }

    /**
     * Send the bot to START
     */
    protected void goHome() {
        if (!bot.hasEnteredGoal() && coverageLimit == 300 && timeLimit == 3600) {
            AStarHeuristicSearch goToGoal = new AStarHeuristicSearch(exploredArenaMap, bot);
            String actionString = goToGoal.runFastestPath(AgentSettings.GOAL_ROW, AgentSettings.GOAL_COL);

            if (!bot.isSim()) NetworkMgr.getInstance().sendMsg("K" + actionString, NetworkMgr.INSTRUCTIONS);
        }

        AStarHeuristicSearch returnToStart = new AStarHeuristicSearch(exploredArenaMap, bot);
        String actionString = returnToStart.runFastestPath(AgentSettings.START_ROW, AgentSettings.START_COL);
        if (!bot.isSim()) NetworkMgr.getInstance().sendMsg("K" + actionString, NetworkMgr.INSTRUCTIONS);

        System.out.println("Exploration complete!");
        areaExplored = calculateAreaExplored();
        System.out.printf("%.2f%% Coverage", (areaExplored / 300.0) * 100.0);
        System.out.println(", " + areaExplored + " Cells");
        System.out.println((getElapsedTime()) / 1000 + " Seconds");

    }

    void alignBeforeFastestPath() {
        // Align before fastest path
        if (!bot.isSim()) {
            turnBotDirection(Direction.SOUTH);
            moveBot(Actions.ALIGN_RIGHT);
            moveBot(Actions.ALIGN_FRONT);
            turnBotDirection(Direction.WEST);
            moveBot(Actions.ALIGN_FRONT);
            turnBotDirection(Direction.EAST);
            moveBot(Actions.ALIGN_RIGHT);
        }
        turnBotDirection(Direction.NORTH);
        System.out.println("Went home");
    }

    protected void goToPoint(int row, int col) {
        goToPoint(new Point(col, row));
    }

    protected void goToPoint(Point coord) {
        System.out.println(coord);
        System.out.println(bot.getAgtPos());
        if (coord.x == bot.getAgtX() && coord.y == bot.getAgtY()) {
            System.out.println("[goToPoint()] Same position, fastest path skipped.");
            return;
        }
        AStarHeuristicSearch goToPoint = new AStarHeuristicSearch(exploredArenaMap, bot);
        String mergedOutputString = parseFastestPathString(goToPoint.runFastestPath(coord.y, coord.x));

        if (!bot.isSim()) NetworkMgr.getInstance().sendMsg("K" + mergedOutputString, NetworkMgr.INSTRUCTIONS);
    }

    /**
     * Send bot back home following the original route
     * Shall only be used in simulation
     */
    protected void goHomeSlow() {
        ArrayList<Actions> reversedActions = reverseActions();
        for (Actions m : reversedActions) {
            moveBot(m);
        }
    }

    /**
     * Find all unexplored cell (can or cannot be reached by bot)
     * @return ArrayList of all unexplored cells
     */
    protected ArrayList<Cell> findUnexplored() {
        Cell curCell, topCell, rightCell;
        int curRow, curCol;
        Queue<Cell> queue= new LinkedList<>();
        HashSet<Cell> hasSeen = new HashSet<>();
        ArrayList<Cell> result = new ArrayList<>();

        curCell = exploredArenaMap.getCell(0, 0);
        queue.add(curCell);
        hasSeen.add(curCell);
        while (queue.size() != 0) {
            curCell = queue.remove();
            curRow = curCell.getRow(); curCol = curCell.getCol();
            if (!curCell.isExplored() ) result.add(curCell);

            if (curRow + 1 < MapSettings.MAP_ROWS && curCol < MapSettings.MAP_COLS) {
                topCell = exploredArenaMap.getCell(curRow + 1, curCol);
                if (!hasSeen.contains(topCell)) {
                    hasSeen.add(topCell);
                    queue.add(topCell);
                }
            }

            if (curRow < MapSettings.MAP_ROWS && curCol + 1 < MapSettings.MAP_COLS) {
                rightCell = exploredArenaMap.getCell(curRow, curCol + 1);
                if (!hasSeen.contains(rightCell)) {
                    hasSeen.add(rightCell);
                    queue.add(rightCell);
                }
            }
        }
        return result;
    }

    /**
     * find the closest reachable cell that is not blocked by obstacle near the target cell
     * @return
     */
    protected Cell findSurroundingReachable(int row, int col) {
        boolean leftClear = true, rightClear = true, topClear = true, botClear = true;
        int offset = 1;
        Cell tmpCell;
        while (true) {
            // bot
            if (row - offset >= 0) {
                tmpCell = exploredArenaMap.getCell(row - offset, col);
                if (!tmpCell.isExplored() || tmpCell.isObstacle()) botClear = false;
                else if (botClear && !tmpCell.isObstacle() && !tmpCell.isVirtualWall()) return tmpCell;
            }

            // left
            if (col - offset >= 0) {
                tmpCell = exploredArenaMap.getCell(row, col - offset);
                if (!tmpCell.isExplored() || tmpCell.isObstacle()) leftClear = false;
                else if (leftClear && !tmpCell.isObstacle() && !tmpCell.isVirtualWall()) return tmpCell;
            }

            // right
            if (row + offset < MapSettings.MAP_ROWS) {
                tmpCell = exploredArenaMap.getCell(row + offset, col);
                if (!tmpCell.isExplored() || tmpCell.isObstacle()) rightClear = false;
                else if (rightClear && !tmpCell.isObstacle() && !tmpCell.isVirtualWall()) return tmpCell;
            }

            // top
            if (col + offset < MapSettings.MAP_COLS) {
                tmpCell = exploredArenaMap.getCell(row, col + offset);
                if (!tmpCell.isExplored() || tmpCell.isObstacle()) topClear = false;
                else if (topClear && !tmpCell.isObstacle() && !tmpCell.isVirtualWall()) return tmpCell;
            }

            if (!topClear && !botClear && !leftClear && !rightClear) return null;

            offset++;
        }
    }

    /**
     * Returns true for cells that are explored and not obstacles.
     */
    protected boolean isExploredNotObstacle(int r, int c) {
//        System.out.println(exploredArenaMap.getCell(r, c));
        if (exploredArenaMap.checkValidCell(r, c)) {
            Cell tmp = exploredArenaMap.getCell(r, c);
            return (tmp.isExplored() && !tmp.isObstacle());
        }
        return false;
    }

    /**
     * Returns true for cells that are explored, not virtual walls and not obstacles.
     */
    protected boolean isExploredAndFree(int r, int c) {
        if (exploredArenaMap.checkValidCell(r, c)) {
            Cell b = exploredArenaMap.getCell(r, c);
            return (b.isExplored() && !b.isVirtualWall() && !b.isObstacle());
        }
        return false;
    }

    /**
     * Returns the number of cells explored in the grid.
     */
    protected int calculateAreaExplored() {
        int result = 0;
        for (int r = 0; r < MapSettings.MAP_ROWS; r++) {
            for (int c = 0; c < MapSettings.MAP_COLS; c++) {
                if (exploredArenaMap.getCell(r, c).isExplored()) {
                    result++;
                }
            }
        }
        return result;
    }

    /**
     * reverse the action list, should only be used in simulation mode;
     */
    protected ArrayList<Actions> reverseActions() {
        ArrayList<Actions> reversedActions = new ArrayList<>();
        reversedActions.add(Actions.FACE_RIGHT);
        reversedActions.add(Actions.FACE_RIGHT);

        for (int i = 0; i < actionsTaken.size(); i++) {
            Actions m = actionsTaken.get(actionsTaken.size() - 1 - i);
            if (m == Actions.FORWARD) reversedActions.add(Actions.FORWARD);
            else if (m == Actions.FACE_LEFT) reversedActions.add(Actions.FACE_RIGHT);
            else if (m == Actions.FACE_RIGHT) reversedActions.add(Actions.FACE_LEFT);
        }
        return reversedActions;
    }

    /**
     * Moves the bot, repaints the map and calls senseAndRepaint().
     */
    protected void moveBot(Actions m) {
        if (!bot.isSim() && m != Actions.ALIGN_FRONT && m != Actions.ALIGN_RIGHT) {
            if (canAlignRight(bot.getAgtDir()) && canAlignFront(bot.getAgtDir())) {
                System.out.println("If corner");
                calibrateBot(Direction.clockwise90(bot.getAgtDir()));
                moveBot(Actions.ALIGN_FRONT);
                moveBot(Actions.ALIGN_RIGHT);
            }
        }

        //        System.out.println("[Agent Dir] " + bot.getAgtDir());
        System.out.println("Action executed: " + m);
        actionsTaken.add(m);
        bot.takeAction(m, 1, exploredArenaMap, realArenaMap);

        senseAndRepaint();

    }

    /**
     * Sets the bot's sensors and processes the sensor data.
     */
    protected int[] sense() {
        bot.setSensors();
        int[] sensorReadings = bot.senseEnv(exploredArenaMap, realArenaMap);

        return sensorReadings;
    }

    /**
     * Sets the bot's sensors, processes the sensor data and repaints the map.
     */
    protected int[] senseAndRepaint() {
        int[] sensorReadings = this.sense();
        exploredArenaMap.repaint();

        return sensorReadings;
    }

//    /**
//     * take picture and send out coordinate is there are walls/obstacles in surrounding
//     */
//    protected void tryTakePicture() {
//        Direction botDir = bot.getAgtDir();
//        for (int offset = AgentSettings.SHORT_MIN; offset <= AgentSettings.SHORT_MAX; offset++) {
//            if (canTakePicture(botDir, offset)) {
//                if (botDir == Direction.NORTH) bot.takePicture(bot.getAgtRow(), bot.getAgtCol() + (1 + offset));
//                else if (botDir == Direction.EAST) bot.takePicture(bot.getAgtRow() - (1 + offset), bot.getAgtCol());
//                else if (botDir == Direction.WEST) bot.takePicture(bot.getAgtRow() + (1 + offset), bot.getAgtCol());
//                else bot.takePicture(bot.getAgtRow(), bot.getAgtCol() - (1 + offset));
//                break;
//            }
//        }
//    }

//    /**
//     * Check if the if the center cell on the RHS of the bot is wall/obstacle so can take picture
//     */
//    private boolean canTakePicture(Direction botDir, int offset) {
//        int row = bot.getAgtRow();
//        int col = bot.getAgtCol();
//
//        switch (botDir) {
//            case NORTH:
//                return exploredArenaMap.isWallOrObstacleCell(row, col + (1 + offset));
//            case EAST:
//                return exploredArenaMap.isWallOrObstacleCell(row - (1 + offset), col);
//            case SOUTH:
//                return exploredArenaMap.isWallOrObstacleCell(row, col - (1 + offset));
//            case WEST:
//                return exploredArenaMap.isWallOrObstacleCell(row + (1 + offset), col);
//        }
//
//        return false;
//    }


    /**
     * Checks if there's wall/obstacle in front of the bot so can alignfront
     */
    boolean canAlignFront(Direction botDir) {
        int row = bot.getAgtRow();
        int col = bot.getAgtCol();

        switch (botDir) {
            case NORTH:
                return exploredArenaMap.isWallOrObstacleCell(row + 2, col - 1) && exploredArenaMap.isWallOrObstacleCell(row + 2, col) && exploredArenaMap.isWallOrObstacleCell(row + 2, col + 1);
            case EAST:
                return exploredArenaMap.isWallOrObstacleCell(row + 1, col + 2) && exploredArenaMap.isWallOrObstacleCell(row, col + 2) && exploredArenaMap.isWallOrObstacleCell(row - 1, col + 2);
            case SOUTH:
                return exploredArenaMap.isWallOrObstacleCell(row - 2, col - 1) && exploredArenaMap.isWallOrObstacleCell(row - 2, col) && exploredArenaMap.isWallOrObstacleCell(row - 2, col + 1);
            case WEST:
                return exploredArenaMap.isWallOrObstacleCell(row + 1, col - 2) && exploredArenaMap.isWallOrObstacleCell(row, col - 2) && exploredArenaMap.isWallOrObstacleCell(row - 1, col - 2);
        }

        return false;
    }

    /**
     * Checks if there's wall/obstacle at RHS of the bot so can align right
     */
    boolean canAlignRight(Direction botDir) {
//        System.out.println(canAlignFront(Direction.clockwise90(botDir)));
        return canAlignFront(Direction.clockwise90(botDir));
    }


    /**
     * Turns the bot in the needed direction and sends the ALIGN_FRONT movement. Once calibrated, the bot is turned back
     * to its original direction.
     */
    private void calibrateBot(Direction targetDir) {
        Direction origDir = bot.getAgtDir();

        turnBotDirection(targetDir);
        if (canAlignFront(targetDir)) moveBot(Actions.ALIGN_FRONT);
        turnBotDirection(origDir);
    }

    /**
     * Turns the robot to the required direction.
     */
    protected void turnBotDirection(Direction targetDir) {
        int numOfTurn = Math.abs(bot.getAgtDir().ordinal() - targetDir.ordinal()) / 2;
        if (numOfTurn > 2) numOfTurn = numOfTurn % 2;

        if (numOfTurn == 1) {
            if (Direction.clockwise90(bot.getAgtDir()) == targetDir) {
                bot.takeAction(Actions.FACE_RIGHT, 0, exploredArenaMap, realArenaMap);
                senseAndRepaint();
            } else {
                bot.takeAction(Actions.FACE_LEFT, 0, exploredArenaMap, realArenaMap);
                senseAndRepaint();
            }
        } else if (numOfTurn == 2) {
            bot.takeAction(Actions.FACE_RIGHT, 0, exploredArenaMap, realArenaMap);
            senseAndRepaint();
            bot.takeAction(Actions.FACE_RIGHT, 0, exploredArenaMap, realArenaMap);
            senseAndRepaint();
        }

    }

    protected long getElapsedTime() {
        currentTime = System.currentTimeMillis();
        if (SIM) return Math.abs(currentTime - startTime) * SimulatorSettings.SIM_ACCELERATION;
        else return Math.abs(currentTime - startTime);
    }
}
