#include "json_parser.h"

#include <cctype>
#include <cstdlib>

namespace lumina::json {

JsonParser::JsonParser(const std::string& text) : text_(text), pos_(0) {}

std::optional<JsonValue> JsonParser::parse() {
    skipWhitespace();
    auto value = parseValue();
    if (!value) return std::nullopt;
    skipWhitespace();
    return value;
}

void JsonParser::skipWhitespace() {
    while (pos_ < text_.size() && std::isspace(static_cast<unsigned char>(text_[pos_]))) {
        ++pos_;
    }
}

bool JsonParser::match(const std::string& token) {
    if (text_.compare(pos_, token.size(), token) == 0) {
        pos_ += token.size();
        return true;
    }
    return false;
}

std::optional<JsonValue> JsonParser::parseValue() {
    skipWhitespace();
    if (pos_ >= text_.size()) return std::nullopt;

    char c = text_[pos_];
    if (c == 'n') return parseNull();
    if (c == 't' || c == 'f') return parseBool();
    if (c == '"') return parseString();
    if (c == '{') return parseObject();
    if (c == '[') return parseArray();
    if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
    return std::nullopt;
}

std::optional<JsonValue> JsonParser::parseNull() {
    if (match("null")) return JsonValue();
    return std::nullopt;
}

std::optional<JsonValue> JsonParser::parseBool() {
    if (match("true")) return JsonValue(true);
    if (match("false")) return JsonValue(false);
    return std::nullopt;
}

std::optional<JsonValue> JsonParser::parseNumber() {
    size_t start = pos_;
    if (text_[pos_] == '-') ++pos_;
    while (pos_ < text_.size() && std::isdigit(static_cast<unsigned char>(text_[pos_]))) ++pos_;
    if (pos_ < text_.size() && text_[pos_] == '.') {
        ++pos_;
        while (pos_ < text_.size() && std::isdigit(static_cast<unsigned char>(text_[pos_]))) ++pos_;
    }
    if (pos_ < text_.size() && (text_[pos_] == 'e' || text_[pos_] == 'E')) {
        ++pos_;
        if (pos_ < text_.size() && (text_[pos_] == '+' || text_[pos_] == '-')) ++pos_;
        while (pos_ < text_.size() && std::isdigit(static_cast<unsigned char>(text_[pos_]))) ++pos_;
    }

    double value = std::strtod(text_.c_str() + start, nullptr);
    return JsonValue(value);
}

std::optional<JsonValue> JsonParser::parseString() {
    if (text_[pos_] != '"') return std::nullopt;
    ++pos_;
    std::string result;
    while (pos_ < text_.size()) {
        char c = text_[pos_++];
        if (c == '"') break;
        if (c == '\\') {
            if (pos_ >= text_.size()) break;
            char esc = text_[pos_++];
            switch (esc) {
                case '"': result.push_back('"'); break;
                case '\\': result.push_back('\\'); break;
                case '/': result.push_back('/'); break;
                case 'b': result.push_back('\b'); break;
                case 'f': result.push_back('\f'); break;
                case 'n': result.push_back('\n'); break;
                case 'r': result.push_back('\r'); break;
                case 't': result.push_back('\t'); break;
                case 'u': {
                    if (pos_ + 4 <= text_.size()) pos_ += 4; // skip unicode surrogate handling
                    break;
                }
                default: result.push_back(esc); break;
            }
        } else {
            result.push_back(c);
        }
    }
    return JsonValue(std::move(result));
}

std::optional<JsonValue> JsonParser::parseObject() {
    if (text_[pos_] != '{') return std::nullopt;
    ++pos_;
    JsonValue::object_t object;
    skipWhitespace();
    if (pos_ < text_.size() && text_[pos_] == '}') { ++pos_; return JsonValue(std::move(object)); }

    while (pos_ < text_.size()) {
        skipWhitespace();
        auto key = parseString();
        if (!key || !key->isString()) return std::nullopt;
        skipWhitespace();
        if (pos_ >= text_.size() || text_[pos_] != ':') return std::nullopt;
        ++pos_;
        auto value = parseValue();
        if (!value) return std::nullopt;
        object.emplace(*key->asString(), std::move(*value));

        skipWhitespace();
        if (pos_ < text_.size() && text_[pos_] == ',') { ++pos_; continue; }
        if (pos_ < text_.size() && text_[pos_] == '}') { ++pos_; break; }
    }
    return JsonValue(std::move(object));
}

std::optional<JsonValue> JsonParser::parseArray() {
    if (text_[pos_] != '[') return std::nullopt;
    ++pos_;
    JsonValue::array_t arr;
    skipWhitespace();
    if (pos_ < text_.size() && text_[pos_] == ']') { ++pos_; return JsonValue(std::move(arr)); }

    while (pos_ < text_.size()) {
        auto value = parseValue();
        if (!value) return std::nullopt;
        arr.emplace_back(std::move(*value));
        skipWhitespace();
        if (pos_ < text_.size() && text_[pos_] == ',') { ++pos_; continue; }
        if (pos_ < text_.size() && text_[pos_] == ']') { ++pos_; break; }
    }
    return JsonValue(std::move(arr));
}

double getNumberField(const JsonValue::object_t& obj, const char* key, double fallback) {
    auto it = obj.find(key);
    if (it != obj.end()) {
        if (auto n = it->second.asNumber()) return *n;
    }
    return fallback;
}

lumina::Vec2 parseVec2(const JsonValue::object_t& obj, const char* key, const lumina::Vec2& fallback) {
    auto it = obj.find(key);
    if (it != obj.end() && it->second.isObject()) {
        if (const auto* o = it->second.asObject()) {
            float x = static_cast<float>(getNumberField(*o, "x", fallback.x));
            float y = static_cast<float>(getNumberField(*o, "y", fallback.y));
            return lumina::Vec2(x, y);
        }
    }
    return fallback;
}

lumina::Vec3 parseVec3(const JsonValue::object_t& obj, const char* key, const lumina::Vec3& fallback) {
    auto it = obj.find(key);
    if (it != obj.end() && it->second.isObject()) {
        if (const auto* o = it->second.asObject()) {
            float x = static_cast<float>(getNumberField(*o, "x", fallback.x));
            float y = static_cast<float>(getNumberField(*o, "y", fallback.y));
            float z = static_cast<float>(getNumberField(*o, "z", fallback.z));
            return lumina::Vec3(x, y, z);
        }
    }
    return fallback;
}

lumina::ColorRGBA parseColor(const JsonValue::object_t& obj, const char* key, const lumina::ColorRGBA& fallback) {
    auto it = obj.find(key);
    if (it != obj.end() && it->second.isObject()) {
        if (const auto* o = it->second.asObject()) {
            float r = static_cast<float>(getNumberField(*o, "r", fallback.r));
            float g = static_cast<float>(getNumberField(*o, "g", fallback.g));
            float b = static_cast<float>(getNumberField(*o, "b", fallback.b));
            float a = static_cast<float>(getNumberField(*o, "a", fallback.a));
            return lumina::ColorRGBA(r, g, b, a);
        }
    }
    return fallback;
}

} // namespace lumina::json
