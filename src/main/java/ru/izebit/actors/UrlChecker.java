package ru.izebit.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import ru.izebit.Offer;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static ru.izebit.ApplicationLauncher.Commands;
import static ru.izebit.ApplicationLauncher.Flags;

/**
 * Актор проверящий доступность ссылок
 * Проверка происходит следующим образом -
 * отправляется get запрос по адресу, если http код возрата не равен 2**, то страница не доступна.
 * <p/>
 * Created by Artem Konovalov on 9/5/15.
 */
public class UrlChecker extends UntypedActor {
    public static final String NAME = "url-checker";
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    public static final int INSTANCE_COUNT = 200;
    //в миллисекундах
    public static final int CONNECTION_TIMEOUT = 30000;

    private static final ReentrantLock lock = new ReentrantLock();
    private static final Set<String> urlCheckerSet = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private HttpClient client;

    @Override
    public void preStart() {
        urlCheckerSet.add(getSelf().toString());

        client = new HttpClient();
        client.setConnectTimeout(CONNECTION_TIMEOUT);
        try {
            client.start();
        } catch (Exception e) {
            logger.error("ошибка инициализации http клиента", e);
        }
    }

    @Override
    public void postStop() {
        urlCheckerSet.remove(getSelf().toString());
    }

    public void onReceive(Object message) throws Exception {
        if (lock.isLocked()) {
            urlCheckerSet.remove(getSelf().toString());
            return;
        }

        if (message instanceof Offer) {
            Offer offer = (Offer) message;

            boolean isAvailable;
            try {
                ContentResponse response = client.GET(offer.getPictureURL().replaceAll("[\\s]+", "%20"));
                isAvailable = HttpStatus.isSuccess(response.getStatus());

            } catch (Exception e) {
                isAvailable = false;
            }

            if (!isAvailable) {
                offer.setCheckResults(offer.getCheckResults() + Flags.PICTURE_IS_NOT_AVAILABLE);
            }
            System.out.println(offer);
        } else if (message == Commands.FINISH_READ) {
            lock.lock();
            try {
                urlCheckerSet.remove(getSelf().toString());
                ActorSelection urlCheckers = getContext().system().actorSelection(
                        "user/" + ModificationChecker.NAME + "/" + UrlChecker.NAME + "/*");
                urlCheckers.tell(Commands.FINISH_READ, ActorRef.noSender());
                while (!urlCheckerSet.isEmpty()) {
                    TimeUnit.SECONDS.sleep(5);
                }
            } finally {
                lock.unlock();
            }
            getContext().system().shutdown();
            System.exit(0);

        } else {
            unhandled(message);
        }
    }
}
