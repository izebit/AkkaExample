package ru.izebit.actors;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static ru.izebit.ApplicationLauncher.Commands;
import static ru.izebit.ApplicationLauncher.Flags;

/**
 * Актор проверящий доступность ссылок
 * Проверка происходит следующим образом -
 * отправляется get запрос по адресу, если http код возрата не равен 2**, то страница не доступна.
 * <p/>
 * полученый результат он отправляет далее на обработку в формате
 * <идентификатор>:<цена>:<p - если ссылка недоступна, иначе не заполнено>
 * <p/>
 * Created by Artem Konovalov on 9/5/15.
 */
public class UrlChecker extends UntypedActor {
    public static final String NAME = "url-checker";
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    public static final int INSTANCE_COUNT = 20;
    //в миллисекундах
    public static final int CONNECTION_TIMEOUT = 30000;

    private ActorRef consumer = getContext().system().actorFor("user/" + ModificationChecker.NAME);
    private HttpClient client;

    @Override
    public void preStart() {
        client = new HttpClient();
        client.setConnectTimeout(CONNECTION_TIMEOUT);
        try {
            client.start();
        } catch (Exception e) {
            logger.error("ошибка инициализации http клиента", e);
        }
    }

    public void onReceive(Object message) throws Exception {
        if (message instanceof String) {
            int index = ((String) message).lastIndexOf(FileReader.DELIMITER_SYMBOL);
            String urlAddress = ((String) message).substring(index + 1);

            boolean isAvailable;
            try {
                ContentResponse response = client.GET(urlAddress.replaceAll("[\\s]+","%20"));
                isAvailable = HttpStatus.isSuccess(response.getStatus());

            } catch (Exception e) {
                isAvailable = false;
            }

            String msg = ((String) message).substring(0, index + 1);
            if (!isAvailable) {
                msg = msg + Flags.PICTURE_IS_NOT_AVAILABLE;
                System.out.println(urlAddress);
            }
            consumer.tell(msg, ActorRef.noSender());

        } else if (message instanceof Commands) {
            consumer.tell(message, ActorRef.noSender());
        } else {
            unhandled(message);
        }
    }
}
