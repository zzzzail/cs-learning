package alg.leetcode;

/**
 * @link https://leetcode-cn.com/problems/minesweeper/
 * 扫雷游戏
 * M 代表一个未外出的地雷
 * X 表示一个已挖出的地雷
 * E 代表一个未挖出的空方块
 * B 代表没有相邻地雷的 已挖出的空白方块
 * 数字 1 到 8 表示有多少地雷与这块已挖出的方块相邻
 */
public class P529Minesweeper {
    
    public static void main(String[] args) {
    
    }
    
    // 定义 8 个方向
    int[] dx = {-1, 1, 0, 0, -1, 1, -1, 1};
    int[] dy = {0, 0, -1, 1, -1, 1, 1, -1};
    
    public char[][] updateBoard(char[][] board, int[] click) {
        int x = click[0], y = click[1];
        // 1. 若起点是雷，游戏结束，直接修改 board 并返回。
        if (board[x][y] == 'M') {
            board[x][y] = 'X';
        }
        else { // 2. 若起点是空地，则从起点开始向 8 邻域的空地进行深度优先搜索。
            dfs(board, x, y);
        }
        return board;
    }
    
    private void dfs(char[][] board, int i, int j) {
        // 递归终止条件：判断空地 (i, j) 周围是否有雷，若有，则将该位置修改为雷数，终止该路径的搜索。
        int cnt = 0;
        for (int k = 0; k < 8; k++) {
            int x = i + dx[k];
            int y = j + dy[k];
            if (x < 0 || x >= board.length || y < 0 || y >= board[0].length) {
                continue;
            }
            if (board[x][y] == 'M') {
                cnt++;
            }
        }
        if (cnt > 0) {
            board[i][j] = (char) ('0' + cnt);
            return;
        }
        
        // 若空地 (i, j) 周围没有雷，则将该位置修改为 ‘B’，向 8 邻域的空地继续搜索。
        board[i][j] = 'B';
        for (int k = 0; k < 8; k++) {
            int x = i + dx[k];
            int y = j + dy[k];
            if (x < 0 || x >= board.length || y < 0 || y >= board[0].length || board[x][y] != 'E') {
                continue;
            }
            dfs(board, x, y);
        }
    }
    
}
