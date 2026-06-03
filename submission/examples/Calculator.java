import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class Calculator extends Application {

    private TextField display;
    private TextField expressionLabel;
    private CalculatorLogic logic = new CalculatorLogic();

    private double firstNumber = 0;
    private double secondNumber = 0;
    private String operator = "";
    private boolean newInput = true;
    private boolean canRepeatEquals = false;

    @Override
    public void start(Stage primaryStage) 
    {
        expressionLabel = new TextField("");
        expressionLabel.setEditable(false);
        expressionLabel.setAlignment(Pos.CENTER_RIGHT);
        expressionLabel.setFont(Font.font("Arial", 14));
        expressionLabel.setPrefHeight(30);
        expressionLabel.setStyle("-fx-background-color: #1a1025; -fx-text-fill: #9980b3; -fx-border-width: 0;");

        display = new TextField("0");
        display.setEditable(false);
        display.setAlignment(Pos.CENTER_RIGHT);
        display.setFont(Font.font("Arial", 28));
        display.setPrefHeight(60);
        display.setStyle("-fx-background-color: #120b1a; -fx-text-fill: #e0d0f0; -fx-border-color: #3d2a54;");

        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setPadding(new Insets(10));

        Button btn0 = makeButton("0", "#2d1f3d");
        btn0.setOnAction(e -> numberPressed("0"));

        Button btn1 = makeButton("1", "#2d1f3d");
        btn1.setOnAction(e -> numberPressed("1"));

        Button btn2 = makeButton("2", "#2d1f3d");
        btn2.setOnAction(e -> numberPressed("2"));

        Button btn3 = makeButton("3", "#2d1f3d");
        btn3.setOnAction(e -> numberPressed("3"));

        Button btn4 = makeButton("4", "#2d1f3d");
        btn4.setOnAction(e -> numberPressed("4"));

        Button btn5 = makeButton("5", "#2d1f3d");
        btn5.setOnAction(e -> numberPressed("5"));

        Button btn6 = makeButton("6", "#2d1f3d");
        btn6.setOnAction(e -> numberPressed("6"));

        Button btn7 = makeButton("7", "#2d1f3d");
        btn7.setOnAction(e -> numberPressed("7"));

        Button btn8 = makeButton("8", "#2d1f3d");
        btn8.setOnAction(e -> numberPressed("8"));

        Button btn9 = makeButton("9", "#2d1f3d");
        btn9.setOnAction(e -> numberPressed("9"));

        Button btnDot = makeButton(".", "#2d1f3d");
        btnDot.setOnAction(e -> {
            if (newInput) 
            {
                display.setText("0.");
                newInput = false;
            } 
            else if (!display.getText().contains(".")) 
            {
                display.setText(display.getText() + ".");
            }
        });

        Button btnAdd = makeButton("+", "#6a3d9a");
        btnAdd.setOnAction(e -> operatorPressed("+"));

        Button btnSub = makeButton("-", "#6a3d9a");
        btnSub.setOnAction(e -> operatorPressed("-"));

        Button btnMul = makeButton("×", "#6a3d9a");
        btnMul.setOnAction(e -> operatorPressed("×"));

        Button btnDiv = makeButton("÷", "#6a3d9a");
        btnDiv.setOnAction(e -> operatorPressed("÷"));

        Button btnEquals = makeButton("=", "#7b2d8e");
        btnEquals.setOnAction(e -> {
            try 
            {
                if (canRepeatEquals && newInput) 
                {
                    double result = logic.calculate(firstNumber, secondNumber, operator);
                    expressionLabel.setText(firstNumber + " " + operator + " " + secondNumber + " =");
                    firstNumber = result;
                    display.setText(Double.toString(result));
                } 
                else if (!operator.isEmpty()) 
                {
                    secondNumber = Double.parseDouble(display.getText());
                    double result = logic.calculate(firstNumber, secondNumber, operator);
                    expressionLabel.setText(firstNumber + " " + operator + " " + secondNumber + " =");
                    firstNumber = result;
                    display.setText(Double.toString(result));
                    canRepeatEquals = true;
                }
                newInput = true;
            } 
            catch (ArithmeticException ex) 
            {
                display.setText("Error: " + ex.getMessage());
                expressionLabel.setText("");
                operator = "";
                canRepeatEquals = false;
                newInput = true;
            } 
            catch (NumberFormatException ex) 
            {
                display.setText("Error: Invalid Input");
                expressionLabel.setText("");
                operator = "";
                canRepeatEquals = false;
                newInput = true;
            }
        });

        Button btnClear = makeButton("C", "#8b2252");
        btnClear.setOnAction(e -> {
            display.setText("0");
            expressionLabel.setText("");
            firstNumber = 0;
            secondNumber = 0;
            operator = "";
            newInput = true;
            canRepeatEquals = false;
        });

        Button btnSqrt = makeButton("√", "#6a3d9a");
        btnSqrt.setOnAction(e -> {
            try 
            {
                double value = Double.parseDouble(display.getText());
                expressionLabel.setText("√" + value);
                display.setText(Double.toString(logic.squareRoot(value)));
                newInput = true;
            } 
            catch (ArithmeticException ex) 
            {
                display.setText("Error: " + ex.getMessage());
                newInput = true;
            } 
            catch (NumberFormatException ex) 
            {
                display.setText("Error: Invalid Input");
                newInput = true;
            }
        });

        Button btnSquare = makeButton("x²", "#6a3d9a");
        btnSquare.setOnAction(e -> {
            try 
            {
                double value = Double.parseDouble(display.getText());
                expressionLabel.setText(value + "²");
                display.setText(Double.toString(logic.power(value, 2)));
                newInput = true;
            } 
            catch (NumberFormatException ex) 
            {
                display.setText("Error: Invalid Input");
                newInput = true;
            }
        });

        grid.add(btnClear, 0, 0);
        grid.add(btnSqrt, 1, 0);
        grid.add(btnSquare, 2, 0);
        grid.add(btnDiv, 3, 0);

        grid.add(btn7, 0, 1);
        grid.add(btn8, 1, 1);
        grid.add(btn9, 2, 1);
        grid.add(btnMul, 3, 1);

        grid.add(btn4, 0, 2);
        grid.add(btn5, 1, 2);
        grid.add(btn6, 2, 2);
        grid.add(btnSub, 3, 2);

        grid.add(btn1, 0, 3);
        grid.add(btn2, 1, 3);
        grid.add(btn3, 2, 3);
        grid.add(btnAdd, 3, 3);

        Button btnPercent = makeButton("%", "#6a3d9a");
        btnPercent.setOnAction(e -> {
            try 
            {
                double value = Double.parseDouble(display.getText());
                display.setText(Double.toString(value / 100.0));
                newInput = true;
            } 
            catch (NumberFormatException ex) 
            {
                display.setText("Error: Invalid Input");
                newInput = true;
            }
        });
        grid.add(btnPercent, 0, 4);
        grid.add(btn0, 1, 4);
        grid.add(btnDot, 2, 4);
        grid.add(btnEquals, 3, 4);

        VBox root = new VBox(5, expressionLabel, display, grid);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #0e0914;");

        Scene scene = new Scene(root, 320, 440);
        primaryStage.setTitle("Calculator");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private Button makeButton(String label, String color) 
    {
        Button btn = new Button(label);
        btn.setPrefSize(70, 55);
        btn.setFont(Font.font("Arial", 18));
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-background-radius: 5;");
        return btn;
    }

    private void numberPressed(String digit) 
    {
        if (newInput) 
        {
            display.setText(digit);
            newInput = false;
            canRepeatEquals = false;
        } 
        else 
        {
            display.setText(display.getText() + digit);
        }
    }

    private void operatorPressed(String op) 
    {
        try 
        {
            if (!operator.isEmpty() && !newInput) 
            {
                double secondNumber = Double.parseDouble(display.getText());
                double result = logic.calculate(firstNumber, secondNumber, operator);
                display.setText(Double.toString(result));
                firstNumber = result;
            } 
            else 
            {
                firstNumber = Double.parseDouble(display.getText());
            }
            operator = op;
            expressionLabel.setText(firstNumber + " " + op);
            newInput = true;
            canRepeatEquals = false;
        } 
        catch (ArithmeticException ex) 
        {
            display.setText("Error: " + ex.getMessage());
            expressionLabel.setText("");
            operator = "";
            newInput = true;
        } 
        catch (NumberFormatException ex) 
        {
            display.setText("Error: Invalid Input");
            expressionLabel.setText("");
            operator = "";
            newInput = true;
        }
    }

    public static void main(String[] args) 
    {
        Application.launch(args);
    }
}