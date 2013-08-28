

TARGET=jx.jar
JX_SO=libjx.so

FILES=java/src/com/mellanox/*.java \

all: $(TARGET)

$(TARGET):$(FILES)
	(cd libxio/; make)
	(cd c; make)
	rm -rf java/bin/*
	javac -d java/bin/ $(FILES)
	(cd java/bin ;jar -cvf $(TARGET) com)
	cp java/bin/$(TARGET) tests/
	cp c/src/libjx.so java/bin
	cp c/src/libjx.so tests/
	cp  libxio/src/usr/libxio.so java/bin
	cp  libxio/src/usr/libxio.so tests/ 
clean:
	(cd c; make distclean)
	rm -rf java/bin/*
	rm -rf tests/$(TARGET)
