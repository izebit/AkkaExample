#  Обработчик файлов
## постановка задачи
Даны два файла со списком товаров, формат файла соответствует файлу в директории test/resources/test.xml  
Необходимо:
* Составить список offer-ов нового файла, у которых недоступна хотя бы одна картинка,
* Составить список идентификаторов новых, изменившихся и исчезнувших товаров.

## Запуск программы
*-jar* **application.jar**   "firstFile.xml" "secondFile.xml"
где первый параметр - это название файла с новым, второй - со старым списком товаров

# Реализация
## Архитектура
Реализовано 3 Актора:
* file-reader  читает файл с новым списком товаров
* url-checker  проверяет url на доступность
* modification-checker опеределяет изменена ли цена товара, по сравнению со старой

В самом начале работы приложения происходит считываение идентификаторов и цен из файла содержащего старый список товаров. 
После инициализации кеша, происходит чтение файла с новыми ценами, фомируются сообщения, которые передаются **url-checker** для проверки доступности 
ссылки. После этого сообщение передается  **modification-checker**, который пытается найти в кэше товар и его предыдующую цену,
основываясь на результате поиска, он составляет итоговое сообщение, которое и выводится пользователю. После того, как
все сообщения были прочитаны из файла, отправляется команда о завершении, она передается по цепочке и в конечном итоге попадает
**modification-checker**, который вычитывает оставшиеся неиспользуемые товары из кеша и показывает их пользователю,с отметкой - *удаленные*.

## Схема
![alt tag](https://raw.githubusercontent.com/izebit/AkkaExample/master/docs/diagram.png)

##Технологии
* java se 8
* framework akka
* hppc (использование хештаблицы)
* HOCON
* gradle