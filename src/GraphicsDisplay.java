import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import javax.swing.*;

public class GraphicsDisplay extends JPanel
{
    // Различные стили черчения линий
    private final BasicStroke graphicsStroke;
    private final BasicStroke axisStroke;
    private final BasicStroke markerStroke;
    // Различные шрифты отображения надписей
    private final Font axisFont;
    private final Font coordinatesFont;
    private final DecimalFormat formatter;
    private final Cursor defaultCursor;
    private final Cursor pointCursor;
    // Список координат точек для построения графика
    private Double[][] graphicsData;
    // Флаговые переменные, задающие правила отображения графика
    private boolean showAxis = true;
    private boolean showMarkers = true;
    // Границы диапазона пространства, подлежащего отображению
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    // Используемый масштаб отображения
    private double scale;
    private boolean isRotated = false;
    private boolean showFilling = false;
    private boolean selection = false;
    private Double[] chosenPoint = null;
    private boolean changingRange = false;
    private Point startRangePoint = null;
    private Point finishRangePoint = null;
    private BasicStroke rangeStroke;
    private ArrayList<Range> ranges;

    private Range pointsToRange(Point p1, Point p2)
    {
        Double[] xy1 = pointToXY(p1);
        Double[] xy2 = pointToXY(p2);

        return new Range(
                Math.min(xy1[0],xy2[0]),
                Math.max(xy1[0],xy2[0]),
                Math.min(xy1[1],xy2[1]),
                Math.max(xy1[1],xy2[1]));
    }

    private class Range
    {
        public Double minX;
        public Double maxX;
        public Double minY;
        public Double maxY;

        public Range(Double minX, Double maxX, Double minY, Double maxY)
        {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    public GraphicsDisplay()
    {
        addMouseMotionListener(new TMouseMotionListener());
        addMouseListener(new TMouseAdapter());
        // Цвет заднего фона области отображения - белый
        setBackground(Color.WHITE);
        // Сконструировать необходимые объекты, используемые в рисовании
        // Перо для рисования графика
        float[] graphDash = new float[]{4, 1, 1, 1, 1, 1, 2, 1, 2};
        graphicsStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, graphDash, 0.0f);
        rangeStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, new float[]{2,1,3,1}, 0.0f);
        // Перо для рисования осей координат
        axisStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, null, 0.0f);
        // Перо для рисования контуров маркеров
        markerStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, null, 0.0f);
        // Шрифт для подписей осей координат
        axisFont = new Font("Serif", Font.BOLD, 36);
        coordinatesFont = new Font("Serif", Font.BOLD, 15);
        formatter = (DecimalFormat) NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(2);
        formatter.setGroupingUsed(false);
        DecimalFormatSymbols dottedDouble = formatter.getDecimalFormatSymbols();
        dottedDouble.setDecimalSeparator('.');
        formatter.setDecimalFormatSymbols(dottedDouble);
        defaultCursor = getCursor();
        pointCursor = new Cursor(Cursor.HAND_CURSOR);
    }

    // Данный метод вызывается из обработчика элемента меню "Открыть файл с графиком"
    // главного окна приложения в случае успешной загрузки данных
    public void showGraphics(Double[][] graphicsData)
    {
        // Сохранить массив точек во внутреннем поле класса
        this.graphicsData = graphicsData;
        ranges = new ArrayList<>();
        // Запросить перерисовку компонента, т.е. неявно вызвать paintComponent();
        Iterator<Double> xit = new Iterator<>() {
            private int i = 0;
            @Override
            public boolean hasNext()
            {
                return i < graphicsData.length;
            }

            @Override
            public Double next()
            {
                return graphicsData[i++][0];
            }
        };
        Iterator<Double> yit = new Iterator<>() {
            private int i = 0;
            @Override
            public boolean hasNext()
            {
                return i < graphicsData.length;
            }
            @Override
            public Double next()
            {
                return graphicsData[i++][1];
            }
        };
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        while (xit.hasNext())
        {
            double next = xit.next();
            if(Double.compare(next,minX)<0)
                minX = next;
            if(Double.compare(next,maxX)>0)
                maxX = next;
        }

        while (yit.hasNext())
        {
            double next = yit.next();
            if(Double.compare(next,minY)<0)
                minY = next;
            if(Double.compare(next,maxY)>0)
                maxY = next;
        }
        ranges.add(new Range(minX,maxX,minY,maxY));
        repaint();
    }
    // Методы-модификаторы для изменения параметров отображения графика
    // Изменение любого параметра приводит к перерисовке области
    public void setShowAxis(boolean showAxis)
    {
        this.showAxis = showAxis;
        repaint();
    }

    public void setShowMarkers(boolean showMarkers)
    {
        this.showMarkers = showMarkers;
        repaint();
    }

    public void setRotated(boolean rotated)
    {
        isRotated = rotated;
        repaint();
    }

    // Метод отображения всего компонента, содержащего график
    public void paintComponent(Graphics g)
    {
        /* Шаг 1 - Вызвать метод предка для заливки области цветом заднего фона
         * Эта функциональность - единственное, что осталось в наследство от
         * * paintComponent класса JPanel */
        super.paintComponent(g);
        // Шаг 2 - Если данные графика не загружены (при показе компонента при запуске программы) - ничего не делать
        if (graphicsData == null || graphicsData.length == 0) return;

        Range r = ranges.get(ranges.size()-1);
        maxX = r.maxX;
        minX = r.minX;
        maxY = r.maxY;
        minY = r.minY;


        double scaleX = getSize().getWidth() / (maxX - minX);
        double scaleY = getSize().getHeight() / (maxY - minY);
        scale = Math.min(scaleX, scaleY);
        if (scale == scaleX)
        {
            double yIncrement = (getSize().getHeight() / scale - (maxY - minY)) / 2;
            maxY += yIncrement;
            minY -= yIncrement;
        }
        if (scale == scaleY)
        {
            // Если за основу был взят масштаб по оси Y, действовать по аналогии
            double xIncrement = (getSize().getWidth() / scale - (maxX - minX)) / 2;
            maxX += xIncrement;
            minX -= xIncrement;
        }
        // Шаг 7 - Сохранить текущие настройки холста
        Graphics2D canvas = (Graphics2D) g;

        Stroke oldStroke = canvas.getStroke();
        Color oldColor = canvas.getColor();
        Paint oldPaint = canvas.getPaint();
        Font oldFont = canvas.getFont();
        // Шаг 8 - В нужном порядке вызвать методы отображения элементов графика
        // Порядок вызова методов имеет значение, т.к. предыдущий рисунок будет затираться последующим
        // Первыми (если нужно) отрисовываются оси координат.
        if (isRotated)
            canvas.rotate(Math.toRadians(-90), getSize().getWidth() / 2, getSize().getHeight() / 2);
        if (showFilling) paintFilling(canvas);
        if (showAxis) paintAxis(canvas);
        // Затем отображается сам график
        paintGraphics(canvas);
        // Затем (если нужно) отображаются маркеры точек, по которым строился график.
        if (showMarkers) paintMarkers(canvas);
        // Шаг 9 - Восстановить старые настройки холста
        if (chosenPoint != null)
        {
            Point2D.Double point = xyToPoint(chosenPoint[0], chosenPoint[1]);
            float pointX = (float) point.x;
            float pointY = (float) point.y;
            canvas.setFont(coordinatesFont);
            canvas.setColor(Color.BLACK);
            String coordinatesString =
                    "X: " + formatter.format(chosenPoint[0]) + " Y: " + formatter.format(chosenPoint[1]);

            canvas.drawString(coordinatesString, pointX + 5, pointY - 8);
        }
        if(changingRange)
        {
            canvas.setColor(Color.BLACK);
            canvas.setStroke(rangeStroke);
            canvas.draw(new Rectangle2D.Double(
                    startRangePoint.x,
                    startRangePoint.y,
                    finishRangePoint.x-startRangePoint.x,
                    finishRangePoint.y-startRangePoint.y));
        }
        canvas.setFont(oldFont);
        canvas.setPaint(oldPaint);
        canvas.setColor(oldColor);
        canvas.setStroke(oldStroke);
    }

    // Отрисовка графика по прочитанным координатам
    protected void paintGraphics(Graphics2D canvas)
    {
        // Выбрать линию для рисования графика
        canvas.setStroke(graphicsStroke);
        // Выбрать цвет линии
        canvas.setColor(Color.RED);
        /* Будем рисовать линию графика как путь, состоящий из множества сегментов (GeneralPath)
         * Начало пути устанавливается в первую точку графика, после чего прямой соединяется со
         * следующими точками */
        GeneralPath graphics = new GeneralPath();
        for (int i = 0; i < graphicsData.length; i++)
        {
            // Преобразовать значения (x,y) в точку на экране point
            Point2D.Double point = xyToPoint(graphicsData[i][0], graphicsData[i][1]);
            if (i > 0)
            {
                // Не первая итерация цикла - вести линию в точку point
                graphics.lineTo(point.getX(), point.getY());
            } else
            {
                // Первая итерация цикла - установить начало пути в точку point
                graphics.moveTo(point.getX(), point.getY());
            }
        }
        // Отобразить график
        canvas.draw(graphics);
    }

    boolean checkPoint(Double[] point)
    {
        Double y = point[1];
        StringBuilder sb = new StringBuilder(y.toString());
        sb.deleteCharAt(sb.indexOf("."));
        String s = sb.substring(0, Math.min(10, sb.length()));
        char c = s.charAt(0);
        int i = 0;
        while (i < s.length() && c++ == s.charAt(i++)) ;
        return i == s.length();
    }

    // Отображение маркеров точек, по которым рисовался график
    protected void paintMarkers(Graphics2D canvas)
    {
        // Шаг 1 - Установить специальное перо для черчения контуров маркеров
        canvas.setStroke(markerStroke);
        // Выбрать красный цвета для контуров маркеров
        Color defaultColor = Color.RED;
        Color highlightColor = Color.BLACK;
        Color chosenColor = Color.BLUE;
        canvas.setColor(defaultColor);
        canvas.setPaint(defaultColor);
        for (Double[] point : graphicsData)
        {
            if (checkPoint(point))
            {
                canvas.setColor(highlightColor);
                canvas.setPaint(highlightColor);
            }
            if (chosenPoint == point)
            {
                canvas.setColor(chosenColor);
                canvas.setPaint(chosenColor);
            }
            Point2D.Double center = xyToPoint(point[0], point[1]);
            Point2D.Double leftTop = shiftPoint(center, -5, -5);
            Point2D.Double leftBot = shiftPoint(center, -5, 5);
            Point2D.Double rightTop = shiftPoint(center, 5, -5);
            Point2D.Double rightBot = shiftPoint(center, 5, 5);
            Point2D.Double centerTop = shiftPoint(center, 0, -5);
            Point2D.Double centerBot = shiftPoint(center, 0, 5);
            Point2D.Double centerLeft = shiftPoint(center, -5, 0);
            Point2D.Double centerRight = shiftPoint(center, 5, 0);
            canvas.draw(new Line2D.Double(leftTop, rightBot));
            canvas.draw(new Line2D.Double(leftBot, rightTop));
            canvas.draw(new Line2D.Double(centerLeft, centerRight));
            canvas.draw(new Line2D.Double(centerBot, centerTop));
            canvas.draw(new Ellipse2D.Double(center.x, center.y, 2, 2));
            canvas.setColor(defaultColor);
            canvas.setPaint(defaultColor);
        }
    }

    private double calcArea(ArrayList<Double[]> polygon)
    {
        double sum = 0.0;
        for (int i = 2; i < polygon.size() - 3; i++)
            sum += 2 * polygon.get(i)[1];
        sum += polygon.get(1)[1];
        sum += polygon.get(polygon.size() - 2)[1];
        return (polygon.get(2)[0] - polygon.get(1)[0]) / 2 * sum;
    }

    protected void paintFilling(Graphics2D canvas)
    {
        ArrayList<Double[]> points = new ArrayList<>(Arrays.asList(graphicsData));
        ArrayList<Integer> zeroes = new ArrayList<>();

        int i = 0;
        while (i < points.size() - 1)
        {
            Double x1 = points.get(i)[0];
            Double x2 = points.get(i + 1)[0];
            Double y1 = points.get(i)[1];
            Double y2 = points.get(i + 1)[1];
            if (Math.signum(y2) == 0.0)
                zeroes.add(i + 2);
            if (Math.signum(y1) * Math.signum(y2) == -1.0)
            {
                zeroes.add(i);
                points.add(i, new Double[]{(x2 * y1 - x1 * y2) / (y1 - y2), 0.0});
            }
            i++;
        }
        points.add(0, new Double[]{points.get(0)[0], 0.0});
        zeroes.add(0, 0);
        points.add(new Double[]{points.get(points.size() - 1)[0], 0.0});
        zeroes.add(points.size() - 1);
        ArrayList<ArrayList<Double[]>> polygons = new ArrayList<>();

        for (int in = 0; in < zeroes.size() - 1; in++)
            polygons.add(new ArrayList<>(points.subList(zeroes.get(in), zeroes.get(in + 1) + 1)));

        for (ArrayList<Double[]> polygon : polygons)
        {
            GeneralPath gp = new GeneralPath();

            double area = calcArea(polygon);
            boolean firstPoint = true;
            for (Double[] point : polygon)
            {
                Point2D.Double p = xyToPoint(point[0], point[1]);
                if (firstPoint)
                {
                    gp.moveTo(p.x, p.y);
                    firstPoint = false;
                } else gp.lineTo(p.x, p.y);
            }
            gp.closePath();
            canvas.setStroke(graphicsStroke);
            canvas.setColor(Color.GREEN);
            canvas.draw(gp);
            canvas.setColor(Color.GREEN);
            canvas.fill(gp);
            Rectangle2D bounds = gp.getBounds2D();
            double y = bounds.getMaxY() - (bounds.getMaxY() - bounds.getMinY()) / 2;
            double delta = (bounds.getMaxX() - bounds.getMinX()) / 10;
            for (double x = bounds.getMinX() + 2 * delta; x <= bounds.getMaxX(); x += delta)
            {
                if (gp.contains(x, y))
                {
                    canvas.setFont(axisFont);
                    canvas.setColor(Color.BLACK);
                    canvas.drawString(formatter.format(area), (float) x, (float) y);
                    break;
                }
            }

        }
    }

    // Метод, обеспечивающий отображение осей координат
    protected void paintAxis(Graphics2D canvas)
    {
        // Установить особое начертание для осей
        canvas.setStroke(axisStroke);
        // Оси рисуются чѐрным цветом
        canvas.setColor(Color.BLACK);
        // Стрелки заливаются чѐрным цветом
        canvas.setPaint(Color.BLACK);
        // Подписи к координатным осям делаются специальным шрифтом
        canvas.setFont(axisFont);
        // Создать объект контекста отображения текста - для получения характеристик устройства (экрана)
        FontRenderContext context = canvas.getFontRenderContext();
        // Определить, должна ли быть видна ось Y на графике
        if (minX <= 0.0 && maxX >= 0.0)
        {
            // Она должна быть видна, если левая граница показываемой области (minX) <= 0.0,
            // а правая (maxX) >= 0.0
            // Сама ось - это линия между точками (0, maxY) и (0, minY)
            canvas.draw(new Line2D.Double(xyToPoint(0, maxY), xyToPoint(0, minY)));
            // Стрелка оси Y
            GeneralPath arrow = new GeneralPath();
            // Установить начальную точку ломаной точно на верхний конец оси Y
            Point2D.Double lineEnd = xyToPoint(0, maxY);
            arrow.moveTo(lineEnd.getX(), lineEnd.getY());
            // Вести левый "скат" стрелки в точку с относительными координатами (5,20)
            arrow.lineTo(arrow.getCurrentPoint().getX() + 5, arrow.getCurrentPoint().getY() + 20);
            // Вести нижнюю часть стрелки в точку с относительными координатами (-10, 0)
            arrow.lineTo(arrow.getCurrentPoint().getX() - 10, arrow.getCurrentPoint().getY());
            // Замкнуть треугольник стрелки
            arrow.closePath();
            canvas.draw(arrow);
            // Нарисовать стрелку
            canvas.fill(arrow);
            // Закрасить стрелку
            // Нарисовать подпись к оси Y
            // Определить, сколько места понадобится для надписи "y"
            Rectangle2D bounds = axisFont.getStringBounds("y", context);
            Point2D.Double labelPos = xyToPoint(0, maxY);
            // Вывести надпись в точке с вычисленными координатами
            canvas.drawString("y", (float) labelPos.getX() + 10, (float) (labelPos.getY() - bounds.getY()));
        }
        // Определить, должна ли быть видна ось X на графике 
        if (minY <= 0.0 && maxY >= 0.0)
        {
            // Она должна быть видна, если верхняя граница показываемой области (maxX) >= 0.0,
            // а нижняя (minY) <= 0.0
            canvas.draw(new Line2D.Double(xyToPoint(minX, 0), xyToPoint(maxX, 0)));
            // Стрелка оси X 
            GeneralPath arrow = new GeneralPath();
            // Установить начальную точку ломаной точно на правый конец оси X
            Point2D.Double lineEnd = xyToPoint(maxX, 0);
            arrow.moveTo(lineEnd.getX(), lineEnd.getY());
            // Вести верхний "скат" стрелки в точку с относительными координатами (-20,-5)
            arrow.lineTo(arrow.getCurrentPoint().getX() - 20, arrow.getCurrentPoint().getY() - 5);
            // Вести левую часть стрелки в точку с относительными координатами (0, 10)
            arrow.lineTo(arrow.getCurrentPoint().getX(), arrow.getCurrentPoint().getY() + 10);
            // Замкнуть треугольник стрелки
            arrow.closePath();
            canvas.draw(arrow);
            // Нарисовать стрелку
            canvas.fill(arrow);
            // Закрасить стрелку
            // Нарисовать подпись к оси X
            // Определить, сколько места понадобится для надписи "x"
            Rectangle2D bounds = axisFont.getStringBounds("x", context);
            Point2D.Double labelPos = xyToPoint(maxX, 0);
            // Вывести надпись в точке с вычисленными координатами
            canvas.drawString("x", (float) (labelPos.getX() - bounds.getWidth() - 10),
                    (float) (labelPos.getY() + bounds.getY()));

        }
    }

    /* Метод-помощник, осуществляющий преобразование координат.
     *Оно необходимо, т.к. верхнему левому углу холста с координатами
     * (0.0, 0.0) соответствует точка графика с координатами (minX, maxY), где
     * minX - это самое "левое" значение X, а
     * maxY - самое "верхнее" значение Y. */
    protected Point2D.Double xyToPoint(double x, double y)
    {
        // Вычисляем смещение X от самой левой точки (minX)
        double deltaX = x - minX;
        // Вычисляем смещение Y от точки верхней точки (maxY)
        double deltaY = maxY - y;
        return new Point2D.Double(deltaX * scale, deltaY * scale);
    }

    /* Метод-помощник, возвращающий экземпляр класса Point2D.Double
     * смещѐнный по отношению к исходному на deltaX, deltaY
     * * К сожалению, стандартного метода, выполняющего такую задачу, нет. */
    protected Point2D.Double shiftPoint(Point2D.Double src, double deltaX, double deltaY)
    {
        // Инициализировать новый экземпляр точки 
        Point2D.Double dest = new Point2D.Double();
        // Задать еѐ координаты как координаты существующей точки + заданные смещения
        dest.setLocation(src.getX() + deltaX, src.getY() + deltaY);
        return dest;
    }

    public void setShowFilling(boolean showFilling)
    {
        this.showFilling = showFilling;
        repaint();
    }

    private Double[] pointToXY(Point p)
    {
        return new Double[]{p.x / scale + minX, maxY - p.y / scale};
    }

    public Double[][] getGraphicsData()
    {
        return graphicsData;
    }

    private class TMouseAdapter extends MouseAdapter
    {
        @Override
        public void mousePressed(MouseEvent ev)
        {
            if (SwingUtilities.isLeftMouseButton(ev) && chosenPoint!=null)
            {
                selection = true;
            }
            else
            if(ranges!=null && SwingUtilities.isLeftMouseButton(ev))
            {
                changingRange = true;
                startRangePoint = ev.getPoint();
                finishRangePoint = startRangePoint;
            }
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent ev)
        {
            if (SwingUtilities.isLeftMouseButton(ev))
            {
                selection = false;
                changingRange = false;
                if(ranges!=null)
                {
                    finishRangePoint = ev.getPoint();
                    ranges.add(pointsToRange(startRangePoint, finishRangePoint));
                }
            }
            repaint();
        }

        @Override
        public void mouseClicked(MouseEvent ev)
        {
            if(SwingUtilities.isRightMouseButton(ev))
            {
                if(ranges!=null && ranges.size()!=1)
                {
                    ranges.remove(ranges.size()-1);
                }
            }
            repaint();
        }
    }

    private class TMouseMotionListener implements MouseMotionListener
    {
        @Override
        public void mouseDragged(MouseEvent e)
        {
            if (selection)
            {
                Double[] xy = pointToXY(e.getPoint());
                chosenPoint[0] = xy[0];
                chosenPoint[1] = xy[1];
            }
            if(changingRange)
            {
                finishRangePoint = e.getPoint();
            }
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent e)
        {
            setCursor(defaultCursor);
            chosenPoint = null;
            int mouseX = e.getX();
            int mouseY = e.getY();
            int pointX;
            int pointY;
            if (graphicsData != null)
            {
                for (Double[] xy : graphicsData)
                {
                    Point2D.Double point = xyToPoint(xy[0], xy[1]);
                    pointX = (int) point.x;
                    pointY = (int) point.y;
                    if (Math.abs(mouseX - pointX) <= 5 && Math.abs(mouseY - pointY) <= 5)
                    {
                        chosenPoint = xy;
                        setCursor(pointCursor);
                        break;
                    }
                }
            }
            repaint();
        }
    }
}