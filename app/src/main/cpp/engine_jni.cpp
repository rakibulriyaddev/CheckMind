// JNI bridge: runs the unmodified Stockfish UCI loop on a thread inside this process.
// std::cin / std::cout are rebound to in-memory buffers, so Kotlin talks to the engine by
// writing command lines and reading output lines, with no pipes or child process.
#include <jni.h>

#include <condition_variable>
#include <deque>
#include <iostream>
#include <mutex>
#include <streambuf>
#include <string>
#include <thread>

extern int stockfish_main(int argc, char* argv[]);

namespace {

constexpr const char* kEndOfStream = "\x01<eof>";

class InBuf : public std::streambuf {
   public:
    void push(const std::string& line) {
        {
            std::lock_guard<std::mutex> lock(m_);
            pending_ += line;
            pending_ += '\n';
        }
        cv_.notify_one();
    }

   protected:
    int_type underflow() override {
        std::unique_lock<std::mutex> lock(m_);
        cv_.wait(lock, [this] { return !pending_.empty(); });
        current_.swap(pending_);
        pending_.clear();
        setg(current_.data(), current_.data(), current_.data() + current_.size());
        return traits_type::to_int_type(*gptr());
    }

   private:
    std::mutex m_;
    std::condition_variable cv_;
    std::string pending_;
    std::string current_;
};

class OutBuf : public std::streambuf {
   public:
    std::string pop() {
        std::unique_lock<std::mutex> lock(m_);
        cv_.wait(lock, [this] { return !lines_.empty(); });
        std::string line = std::move(lines_.front());
        lines_.pop_front();
        return line;
    }

    void finish() { push(kEndOfStream); }

   protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        for (std::streamsize i = 0; i < n; ++i) put(s[i]);
        return n;
    }

    int_type overflow(int_type c) override {
        if (c != traits_type::eof()) put(static_cast<char>(c));
        return traits_type::not_eof(c);
    }

   private:
    void put(char c) {
        if (c == '\n') {
            push(std::move(partial_));
            partial_.clear();
        } else if (c != '\r') {
            partial_ += c;
        }
    }

    void push(std::string line) {
        {
            std::lock_guard<std::mutex> lock(m_);
            lines_.push_back(std::move(line));
        }
        cv_.notify_one();
    }

    std::mutex m_;
    std::condition_variable cv_;
    std::deque<std::string> lines_;
    std::string partial_;
};

InBuf g_in;
OutBuf g_out;
std::once_flag g_started;

}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_com_checkmind_app_engine_StockfishNative_start(JNIEnv*, jobject) {
    std::call_once(g_started, [] {
        std::cin.rdbuf(&g_in);
        std::cout.rdbuf(&g_out);
        std::thread([] {
            char name[] = "stockfish";
            char* argv[] = {name, nullptr};
            stockfish_main(1, argv);
            g_out.finish();
        }).detach();
    });
}

JNIEXPORT void JNICALL Java_com_checkmind_app_engine_StockfishNative_write(JNIEnv* env, jobject, jstring line) {
    const char* chars = env->GetStringUTFChars(line, nullptr);
    g_in.push(chars);
    env->ReleaseStringUTFChars(line, chars);
}

// Blocks until the engine prints a line. Returns null once the engine has exited.
JNIEXPORT jstring JNICALL Java_com_checkmind_app_engine_StockfishNative_readLine(JNIEnv* env, jobject) {
    std::string line = g_out.pop();
    if (line == kEndOfStream) {
        g_out.finish();  // stay at end of stream for any later reader
        return nullptr;
    }
    return env->NewStringUTF(line.c_str());
}

}  // extern "C"
