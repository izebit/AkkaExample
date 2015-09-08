package ru.izebit.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import ru.izebit.ApplicationLauncher;
import ru.izebit.Offer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Актор считывающий информацию из файла
 * <p/>
 * Созданные сообщения отправляются актору проверяющему изменение цен
 * <p/>
 * Created by Artem Konovalov on 9/6/15.
 */
public class FileReader extends UntypedActor {
    public static final String NAME = "file-reader";
    private LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    public static final String OPEN_TAG = "<offer ";
    public static final String CLOSE_TAG = "</offer>";

    private String pathToFile;
    private ActorRef modificationChecker;


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
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message == ApplicationLauncher.Commands.START_READ) {
            if (modificationChecker == null) {
                this.modificationChecker = getContext().system().actorFor("user/"+ModificationChecker.NAME);
            }

            readFile();
        } else {
            unhandled(message);
        }
    }

    private void readFile() throws Exception {
        try (Stream<String> lines = Files.lines(Paths.get(pathToFile))) {

            Iterator<String> iterator = lines.iterator();
            Unmarshaller unmarshaller = JAXBContext.newInstance(Offer.class).createUnmarshaller();
            boolean isNextOpenTag = true;
            StringBuilder context = null;
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (isNextOpenTag) {
                    if (line.trim().startsWith(FileReader.OPEN_TAG)) {
                        context = new StringBuilder();
                        context.append(line);

                        isNextOpenTag = false;
                    }
                } else {
                    context.append(line);
                    if (line.trim().startsWith(FileReader.CLOSE_TAG)) {
                        Offer offer = (Offer) unmarshaller.unmarshal(new StringReader(context.toString()));
                        isNextOpenTag = true;

                        modificationChecker.tell(offer, ActorRef.noSender());
                    }
                }
            }
        } catch (IOException ex) {
            logger.error("ошибка чтения файла", ex);
        }
        //отправка сообщений о завершении чтения
        TimeUnit.MINUTES.sleep(1);
        modificationChecker.tell(ApplicationLauncher.Commands.FINISH_READ, ActorRef.noSender());
    }
}
