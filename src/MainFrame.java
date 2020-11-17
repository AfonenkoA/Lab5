import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.channels.FileChannel;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

public class MainFrame extends JFrame
{
    // Начальные размеры окна приложения
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    // Объект диалогового окна для выбора файлов
    private JFileChooser fileChooser = null;
    // Пункты меню
    private final JCheckBoxMenuItem showAxisMenuItem;
    private final JCheckBoxMenuItem showMarkersMenuItem;
    private final JCheckBoxMenuItem showRotatedMenuItem;
    private final JCheckBoxMenuItem showFillingMenuItem;
    private final JMenuItem saveToFileMenuItem;
    // Компонент-отображатель графика
    private final GraphicsDisplay display = new GraphicsDisplay();
    // Флаг, указывающий на загруженность данных графика
    private boolean fileLoaded = false;

    public MainFrame()
    {
        // Вызов конструктора предка Frame
        super("Построение графиков функций на основе заранее подготовленных файлов");
        // Установка размеров окна
        setSize(WIDTH, HEIGHT);
        // Отцентрировать окно приложения на экране
        setLocationRelativeTo(null);
        // Развѐртывание окна на весь экран
        setExtendedState(MAXIMIZED_BOTH);
        // Создать и установить полосу меню
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);
        // Добавить пункт меню "Файл"
        JMenu fileMenu = new JMenu("Файл");
        menuBar.add(fileMenu);
        // Создать действие по открытию файла
        Action openGraphicsAction = new AbstractAction("Открыть файл с графиком")
        {
            public void actionPerformed(ActionEvent event)
            {
                if (fileChooser == null)
                {
                    fileChooser = new JFileChooser();
                    fileChooser.setCurrentDirectory(new File("."));
                }
                if (fileChooser.showOpenDialog(MainFrame.this) == JFileChooser.APPROVE_OPTION)
                {
                    openGraphics(fileChooser.getSelectedFile());
                }
            }
        };
        // Добавить соответствующий элемент меню
        fileMenu.add(openGraphicsAction);
        // Создать пункт меню "График"
        JMenu graphicsMenu = new JMenu("График");
        menuBar.add(graphicsMenu);
        // Создать действие для реакции на активацию элемента "Показывать оси координат"
        Action showAxisAction = new AbstractAction("Показывать оси координат")
        {
            public void actionPerformed(ActionEvent event)
            {
                // свойство showAxis класса GraphicsDisplay истина, если элемент меню
                // showAxisMenuItem отмечен флажком, и ложь - в противном случае
                display.setShowAxis(showAxisMenuItem.isSelected());
            }
        };
        showAxisMenuItem = new JCheckBoxMenuItem(showAxisAction);
        // Добавить соответствующий элемент в меню
        graphicsMenu.add(showAxisMenuItem);

        // Элемент по умолчанию включен (отмечен флажком)
        showAxisMenuItem.setSelected(true);
         // Повторить действия для элемента "Показывать маркеры точек"
        Action showMarkersAction = new AbstractAction("Показывать маркеры точек")
        {
            public void actionPerformed(ActionEvent event)
            {
                // по аналогии с showAxisMenuItem
                display.setShowMarkers(showMarkersMenuItem.isSelected());
            }
        };
        Action showRotatedAction = new AbstractAction("Повернуть график") {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                display.setRotated(showRotatedMenuItem.isSelected());
            }
        };
        Action showFillingAction = new AbstractAction("Определить замкнутые области") {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                display.setShowFilling(showFillingMenuItem.isSelected());
            }
        };
        Action saveToFileAction = new AbstractAction("Сохранить в файл") {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(fileChooser.showSaveDialog(MainFrame.this)==JFileChooser.APPROVE_OPTION)
                {
                    saveGraphics(fileChooser.getSelectedFile());
                }
            }
        };
        saveToFileMenuItem = new JMenuItem(saveToFileAction);
        fileMenu.add(saveToFileMenuItem);
        fileMenu.addMenuListener(new FileMenuListener());
        saveToFileMenuItem.setEnabled(false);
        showFillingMenuItem = new JCheckBoxMenuItem(showFillingAction);
        graphicsMenu.add(showFillingMenuItem);
        showRotatedMenuItem = new JCheckBoxMenuItem(showRotatedAction);
        graphicsMenu.add(showRotatedMenuItem);
        showMarkersMenuItem =new JCheckBoxMenuItem(showMarkersAction);
        graphicsMenu.add(showMarkersMenuItem);
        // Элемент по умолчанию включен (отмечен флажком)
        showMarkersMenuItem.setSelected(true);
        // Зарегистрировать обработчик событий, связанных с меню "График"
        graphicsMenu.addMenuListener(new GraphicsMenuListener());
        // Установить GraphicsDisplay в цент граничной компоновки
        getContentPane().add(display, BorderLayout.CENTER);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);
    }

    // Считывание данных графика из существующего файла
    protected void saveGraphics(File selectedFile)
    {
        Double[][] graphicsData = display.getGraphicsData();
        try(FileChannel fc = new RandomAccessFile(selectedFile,"rw").getChannel())
        {
            ByteBuffer buffer = ByteBuffer.allocate(graphicsData.length*2*Double.BYTES);
            buffer.order(ByteOrder.nativeOrder());
            for (int i = 0; i < graphicsData.length; i++)
            {
                buffer.putDouble(graphicsData[i][0]);
                buffer.putDouble(graphicsData[i][1]);
            }
            buffer.flip();
            fc.write(buffer);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    protected void openGraphics(File selectedFile)
    {
        try(FileChannel fc = new RandomAccessFile(selectedFile, "rw").getChannel())
        {
            DoubleBuffer db = fc.map(FileChannel.MapMode.READ_WRITE, 0, fc.size())
                    .order(ByteOrder.nativeOrder()).asDoubleBuffer();
            Double[][] graphicsData = new Double[db.remaining()/2][];
            // Шаг 3 - Цикл чтения данных (пока в потоке есть данные)
            int i = 0;
            while (i < graphicsData.length)
                graphicsData[i++] = new Double[]{db.get(), db.get()};

            if (graphicsData.length > 0)
            {
                // Да - установить флаг загруженности данных
                fileLoaded = true;
                // Вызывать метод отображения графика
                display.showGraphics(graphicsData);
            }
            // Шаг 5 - Закрыть входной поток
        } catch (FileNotFoundException ex)
        {
            // В случае исключительной ситуации типа "Файл не найден" показать сообщение об ошибке
            JOptionPane.showMessageDialog(MainFrame.this, "Указанный файл не найден", "Ошибка загрузки данных",
                    JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex)
        {
            // В случае ошибки ввода из файлового потока показать сообщение об ошибке
            JOptionPane.showMessageDialog(MainFrame.this, "Ошибка чтения координат точек из файла", "Ошибка загрузки " +
                    "данных", JOptionPane.WARNING_MESSAGE);
        }
    }
    private class FileMenuListener implements MenuListener
    {

        @Override
        public void menuSelected(MenuEvent e)
        {
            saveToFileMenuItem.setEnabled(fileLoaded);
        }

        @Override
        public void menuDeselected(MenuEvent e)
        {

        }

        @Override
        public void menuCanceled(MenuEvent e)
        {

        }
    }
// Класс-слушатель событий, связанных с отображением меню
private class GraphicsMenuListener implements MenuListener
{
    // Обработчик, вызываемый перед показом меню
    public void menuSelected(MenuEvent e)
    {
        // Доступность или недоступность элементов меню "График" определяется загруженностью данных
        showAxisMenuItem.setEnabled(fileLoaded);
        showMarkersMenuItem.setEnabled(fileLoaded);
        showRotatedMenuItem.setEnabled(fileLoaded);
        showFillingMenuItem.setEnabled(fileLoaded);
    }

    // Обработчик, вызываемый после того, как меню исчезло с экрана
    public void menuDeselected(MenuEvent e)
    {
    }

    // Обработчик, вызываемый в случае отмены выбора пункта меню (очень редкая ситуация)
    public void menuCanceled(MenuEvent e)
    {
    }
}
}