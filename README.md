Библиотека ищет в строке вызовы определенных функций и заменяет их на результат вызова этих функций.

Подключение через Maven:
```xml
        <dependency>
            <groupId>io.github.argcc</groupId>
            <artifactId>string-function</artifactId>
            <version>1.0.0</version>
        </dependency>
```

Пример:

```java
import io.github.argcc.string_function.FuncName;
import io.github.argcc.string_function.StringFunctionHandler;

import java.text.SimpleDateFormat;
import java.util.Date;


public class StringFunctionExample {

    public static class HtmlFunctions {
        public String bold(String text) {
            return "<b>" + text + "</b>";
        }
    }

    public static class MarkdownFunctions {
        public String bold(String text) {
            return "**" + text + "**";
        }
    }

    public static class CommonFunctions {
        private static final SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy");

        public String date() {
            return formatter.format(new Date());
        }
    }

    public static class XPathFunctions {
        private static final String TEXT = "normalize-space(translate(text(),' ', ' '))";

        public String sanitize(String value) {
            return value == null ? "" : value
                    .replace("'", "''")
                    .replace(" ", " ")// два разных пробела: 0xC2A0 и 0x20
                    .replaceAll("\\s{2,}", " ")
                    .trim();
        }

        @FuncName("class")
        public String haveClass(String className) {
            return "contains(concat(' ',@class,' '), ' " + className + " ')";
        }

        public String text(String text) {
            return "(" + TEXT + " = '" + sanitize(text) + "')";
        }

        public String textContain(String text) {
            return "contains(" + TEXT + ", '" + sanitize(text) + "')";
        }

        public String textStartWith(String text) {
            return "starts-with(" + TEXT + ", '" + sanitize(text) + "')";
        }
    }

    public static void main(String[] args) {
        StringFunctionHandler sfh = new StringFunctionHandler()
                .setFunctions(new CommonFunctions(), new HtmlFunctions())
                .setTemplate("name{args}");

        // 'date{}' будет заменен на результат вызова метода CommonFunctions.date
        System.out.println(sfh.handle("Current date is date{}"));
        //OUT: Current date is 28.04.2022

        // В метод могут быть переданы параметры
        System.out.println(sfh.handle("Here is bold{'some bold text'}"));
        //OUT: Here is <b>some bold text</b>

        // Изменение набора вызываемых методов
        sfh.setFunctions(new MarkdownFunctions());
        System.out.println(sfh.handle("Here is bold{'some bold text'}"));
        //OUT: Here is **some bold text**

        // Строковый литерал может обрамляться тремя видами кавычек.
        // Для экранирования внутри литерала, нужно дублировать кавычки.
        System.out.println(sfh.handle("bold{'text with `''\"quotes\"''`'}"));
        //OUT: **text with `'"quotes"'`**
        System.out.println(sfh.handle("bold{\"text with `'\"\"quotes\"\"'`\"}"));
        //OUT: **text with `'"quotes"'`**
        System.out.println(sfh.handle("bold{`text with ``'\"quotes\"'```}"));
        //OUT: **text with `'"quotes"'`**

        // Если метод принимает только один строковый аргумент и метод не перегружен,
        // то строковый литерал может быть указан без кавычек
        System.out.println(sfh.handle("bold{text with `'\"quotes\"'`}"));
        //OUT: **text with `'"quotes"'`**

        // Пример упрощения xpath
        sfh.setFunctions(new XPathFunctions());
        System.out.println(sfh.handle("//*[class{header-item} and text{Some page header}]"));
        //OUT: //*[contains(concat(' ',@class,' '), ' header-item ') and (normalize-space(translate(text(),' ', ' ')) = 'Some page header')]

        // Синтаксис вызова может быть разным
        sfh.setTemplate("$name(args)");
        System.out.println(sfh.handle("//*[$class(header-item) and $text(Some page header)]"));
        //OUT: //*[contains(concat(' ',@class,' '), ' header-item ') and (normalize-space(translate(text(),' ', ' ')) = 'Some page header')]

    }
}
```