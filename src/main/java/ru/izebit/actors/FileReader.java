package ru.izebit.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.routing.RoundRobinPool;
import ru.izebit.ApplicationLauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Актор считывающий информацию из файла
 * и создающий сообщения в формате:
 * <идентификатор>;<цена>;<ссылка>
 * Эти сообщения отправляются актору проверяющему доступность ссылок
 * <p/>
 * Created by Artem Konovalov on 9/6/15.
 */
public class FileReader extends UntypedActor {
    public static final String NAME = "file-reader";
    private LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    public static final char DELIMITER_SYMBOL = ';';
    public static final String PRICE_PREFIX = "<price>";
    public static final String ID_PREFIX = "<offer id=\"";
    private static final String URL_PREFIX = "<picture>";

    private String pathToFile;
    private final ActorRef consumer;


    public static Props props(final String pathToFile) {
        return Props.create(new Creator<FileReader>() {

            private static final long serialVersionUID = 42L;

            @Override
            public FileReader create() throws Exception {
                return new FileReader(pathToFile);
            }
        });
    }

    public FileReader(String pathToFile) {
        this.pathToFile = pathToFile;

        consumer = getContext().actorOf(Props.create(UrlChecker.class)
                .withRouter(new RoundRobinPool(UrlChecker.INSTANCE_COUNT))
                .withMailbox("akka.actor.my-bounded-mailbox"), UrlChecker.NAME);
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message == ApplicationLauncher.Commands.START_READ) {
            readFile();
        } else {
            unhandled(message);
        }
    }

    private void readFile() throws Exception {
        try (Stream<String> lines = Files.lines(Paths.get(pathToFile))) {
            String price = null;
            String identity = null;
            String url = null;

            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.startsWith(ID_PREFIX)) {
                    identity = line.substring(ID_PREFIX.length(), line.indexOf("\"", ID_PREFIX.length()));
                } else if (line.startsWith(URL_PREFIX)) {
                    url = line.substring(URL_PREFIX.length(), line.indexOf("</", URL_PREFIX.length()));
                } else if (line.startsWith(PRICE_PREFIX)) {
                    price = line.substring(PRICE_PREFIX.length(), line.indexOf("</", PRICE_PREFIX.length()));
                }

                if (price != null && identity != null && url != null) {
                    consumer.tell(identity + DELIMITER_SYMBOL + price + DELIMITER_SYMBOL + url, ActorRef.noSender());
                    price = identity = url = null;

                }
            }


        } catch (IOException ex) {
            logger.error("ошибка чтения файла", ex);
        }
        //отправка сообщений о завершении чтения
        TimeUnit.MINUTES.sleep(1);
        ActorSelection actorSelection = getContext().system().actorSelection("user/" + FileReader.NAME + "/" + UrlChecker.NAME + "/*");
        actorSelection.tell(ApplicationLauncher.Commands.FINISH_READ, ActorRef.noSender());
    }
}
