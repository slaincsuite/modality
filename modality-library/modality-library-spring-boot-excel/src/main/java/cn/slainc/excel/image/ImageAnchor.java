package cn.slainc.excel.image;

public record ImageAnchor(int column1, int row1, int column2, int row2) {

    public ImageAnchor {
        if (column1 < 0 || row1 < 0 || column2 < column1 || row2 < row1) {
            throw new IllegalArgumentException("图片锚点坐标非法");
        }
    }

    public static ImageAnchor cell(int row, int column) {
        return new ImageAnchor(column, row, column + 3, row + 3);
    }

    public static ImageAnchor of(int row1, int column1, int row2, int column2) {
        return new ImageAnchor(column1, row1, column2, row2);
    }
}
