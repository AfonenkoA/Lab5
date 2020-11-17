import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import javax.swing.JPanel;

public class GraphicsDisplay extends JPanel
{
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
    // Различные стили черчения линий
    private final BasicStroke graphicsStroke;
    private final BasicStroke axisStroke;
    private final BasicStroke markerStroke;
    // Различные шрифты отображения надписей
    private final Font axisFont;
    private boolean isRotated = false;
    private boolean showFilling = false;

    public GraphicsDisplay()
    {
        // Цвет заднего фона области отображения - белый
        setBackground(Color.WHITE);
        // Сконструировать необходимые объекты, используемые в рисовании
        // Перо для рисования графика
        float[] graphDash = new float[]{4,1,1,1,1,1,2,1,2};
        graphicsStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, graphDash, 0.0f);
        // Перо для рисования осей координат
        axisStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, null, 0.0f);
        // Перо для рисования контуров маркеров
        markerStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, null, 0.0f);
        // Шрифт для подписей осей координат
        axisFont = new Font("Serif", Font.BOLD, 36);
    }

    // Данный метод вызывается из обработчика элемента меню "Открыть файл с графиком"
    // главного окна приложения в случае успешной загрузки данных
    public void showGraphics(Double[][] graphicsData)
    {
        // Сохранить массив точек во внутреннем поле класса
        this.graphicsData = graphicsData;
        // Запросить перерисовку компонента, т.е. неявно вызвать paintComponent();
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
        // Шаг 3 - Определить минимальное и максимальное значения для координат X и Y
        // Это необходимо для определения области пространства, подлежащей отображению
        // Еѐ верхний левый угол это (minX, maxY) - правый нижний это (maxX, minY)
        minX = graphicsData[0][0];
        maxX = graphicsData[graphicsData.length - 1][0];
        minY = graphicsData[0][1];
        maxY = minY;
        // Найти минимальное и максимальное значение функции
        for (int i = 1; i < graphicsData.length; i++)
        {
            if (graphicsData[i][1] < minY)
            {
                minY = graphicsData[i][1];
            }
            if (graphicsData[i][1] > maxY)
            {
                maxY = graphicsData[i][1];
            }
        }
        /* Шаг 4 - Определить (исходя из размеров окна) масштабы по осям X и Y - сколько пикселов
         * приходится на единицу длины по X и по Y */
        double scaleX = getSize().getWidth() / (maxX - minX);
        double scaleY = getSize().getHeight() / (maxY - minY);
        // Шаг 5 - Чтобы изображение было неискажѐнным - масштаб должен быть одинаков
        // Выбираем за основу минимальный
        scale = Math.min(scaleX, scaleY);
        // Шаг 6 - корректировка границ отображаемой области согласно выбранному масштабу
        if (scale == scaleX)
        { /* Если за основу был взят масштаб по оси X, значит по оси Y делений меньше,
         * т.е. подлежащий визуализации диапазон по Y будет меньше высоты окна.
         * Значит необходимо добавить делений, сделаем это так:
         * 1) Вычислим, сколько делений влезет по Y при выбранном масштабе - getSize().getHeight()/scale
         * 2) Вычтем из этого сколько делений требовалось изначально
         * 3) Набросим по половине недостающего расстояния на maxY и minY */
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
        if(isRotated)
        {
            //AffineTransform ntr = new AffineTransform(canvas.getTransform());
            //canvas.setTransform(ntr);
            //ntr.scale(0.5,0.5);
            canvas.rotate(Math.toRadians(-90), getSize().getWidth() / 2, getSize().getHeight() / 2);
        }
        if(showFilling) paintFilling(canvas);
        if (showAxis) paintAxis(canvas);
        // Затем отображается сам график
        paintGraphics(canvas);
        // Затем (если нужно) отображаются маркеры точек, по которым строился график.
        if (showMarkers) paintMarkers(canvas);
        // Шаг 9 - Восстановить старые настройки холста


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
        String s = sb.substring(0,Math.min(10,sb.length()));
        char c = s.charAt(0);
        int i = 0;
        while (i<s.length() && c++==s.charAt(i++));
        return i == s.length();
    }
    // Отображение маркеров точек, по которым рисовался график
    protected void paintMarkers(Graphics2D canvas)
    {
        // Шаг 1 - Установить специальное перо для черчения контуров маркеров
        canvas.setStroke(markerStroke);
        // Выбрать красный цвета для контуров маркеров
        Color defaultColor = Color.RED;
        Color highlightColor = Color.BLUE;
        canvas.setColor(defaultColor);
        canvas.setPaint(defaultColor);
        for (Double[] point : graphicsData)
        {
            if(checkPoint(point))
            {
                canvas.setColor(highlightColor);
                canvas.setPaint(highlightColor);
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
        for (int i = 2; i < polygon.size()-3;i++)
            sum += 2*polygon.get(i)[1];
        sum += polygon.get(1)[1];
        sum += polygon.get(polygon.size()-2)[1];
        return (polygon.get(2)[0]-polygon.get(1)[0])/2*sum;
    }
    protected void paintFilling(Graphics2D canvas)
    {
        ArrayList<Double[]> points = new ArrayList<>(Arrays.asList(graphicsData));
        ArrayList<Integer> zeroes = new ArrayList<>();

        int i = 0;
        while (i < points.size()-1)
        {
            Double x1 = points.get(i)[0];
            Double x2 = points.get(i+1)[0];
            Double y1 = points.get(i)[1];
            Double y2 = points.get(i+1)[1];
            if(Math.signum(y2)==0.0)
                zeroes.add(i+2);
            if (Math.signum(y1) * Math.signum(y2) == -1.0)
            {
                zeroes.add(i);
                points.add(i, new Double[]{(x2 * y1 - x1 * y2) / (y1 - y2), 0.0});
            }
            i++;
        }
        points.add(0,new Double[]{points.get(0)[0],0.0});
        zeroes.add(0,0);
        points.add(new Double[]{points.get(points.size()-1)[0],0.0});
        zeroes.add(points.size()-1);
        ArrayList<ArrayList<Double[]>> polygons = new ArrayList<>();

        for(int in = 0; in < zeroes.size()-1; in++)
            polygons.add(new ArrayList<>(points.subList(zeroes.get(in),zeroes.get(in + 1)+1)));

        DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(5);
        formatter.setGroupingUsed(false);
        DecimalFormatSymbols dottedDouble = formatter.getDecimalFormatSymbols();
        dottedDouble.setDecimalSeparator('.');
        formatter.setDecimalFormatSymbols(dottedDouble);

        for (ArrayList<Double[]> polygon : polygons)
        {
            GeneralPath gp = new GeneralPath();

            double area = calcArea(polygon);
            boolean firstPoint = true;
            for (Double[] point : polygon)
            {
                Point2D.Double p = xyToPoint(point[0],point[1]);
                if(firstPoint)
                {
                    gp.moveTo(p.x, p.y);
                    firstPoint = false;
                }
                else gp.lineTo(p.x,p.y);
            }
            gp.closePath();
            canvas.setStroke(graphicsStroke);
            canvas.setColor(Color.GREEN);
            canvas.draw(gp);
            canvas.setColor(Color.GREEN);
            canvas.fill(gp);
            Rectangle2D bounds = gp.getBounds2D();
            double y = bounds.getMaxY() - (bounds.getMaxY() - bounds.getMinY())/2;
            double delta = (bounds.getMaxX()-bounds.getMinX())/10;
            for(double x = bounds.getMinX()+2*delta; x <= bounds.getMaxX(); x += delta)
            {
                if(gp.contains(x,y))
                {
                    canvas.setFont(axisFont);
                    canvas.setColor(Color.BLACK);
                    canvas.drawString(formatter.format(area),(float) x,(float) y);
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
}