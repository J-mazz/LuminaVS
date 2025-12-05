#ifndef LUMINA_JSON_PARSER_H
#define LUMINA_JSON_PARSER_H

#include <map>
#include <optional>
#include <string>
#include <variant>
#include <vector>

#include "engine_structs.h"

namespace lumina::json {

struct JsonValue {
    using object_t = std::map<std::string, JsonValue>;
    using array_t = std::vector<JsonValue>;

    enum class Type { Null, Bool, Number, String, Object, Array };

    Type type = Type::Null;
    std::variant<std::monostate, bool, double, std::string, object_t, array_t> data;

    JsonValue() = default;
    explicit JsonValue(bool b) : type(Type::Bool), data(b) {}
    explicit JsonValue(double n) : type(Type::Number), data(n) {}
    explicit JsonValue(std::string s) : type(Type::String), data(std::move(s)) {}
    explicit JsonValue(object_t o) : type(Type::Object), data(std::move(o)) {}
    explicit JsonValue(array_t a) : type(Type::Array), data(std::move(a)) {}

    bool isObject() const { return type == Type::Object; }
    bool isArray() const { return type == Type::Array; }
    bool isNumber() const { return type == Type::Number; }
    bool isBool() const { return type == Type::Bool; }
    bool isString() const { return type == Type::String; }

    const object_t* asObject() const { return std::get_if<object_t>(&data); }
    const array_t* asArray() const { return std::get_if<array_t>(&data); }
    std::optional<double> asNumber() const {
        if (const auto* v = std::get_if<double>(&data)) return *v;
        return std::nullopt;
    }
    std::optional<bool> asBool() const {
        if (const auto* v = std::get_if<bool>(&data)) return *v;
        return std::nullopt;
    }
    std::optional<std::string> asString() const {
        if (const auto* v = std::get_if<std::string>(&data)) return *v;
        return std::nullopt;
    }
};

class JsonParser {
public:
    explicit JsonParser(const std::string& text);
    std::optional<JsonValue> parse();

private:
    const std::string& text_;
    size_t pos_;

    void skipWhitespace();
    bool match(const std::string& token);
    std::optional<JsonValue> parseValue();
    std::optional<JsonValue> parseNull();
    std::optional<JsonValue> parseBool();
    std::optional<JsonValue> parseNumber();
    std::optional<JsonValue> parseString();
    std::optional<JsonValue> parseObject();
    std::optional<JsonValue> parseArray();
};

// Helper accessors for object fields

double getNumberField(const JsonValue::object_t& obj, const char* key, double fallback);
lumina::Vec2 parseVec2(const JsonValue::object_t& obj, const char* key, const lumina::Vec2& fallback);
lumina::Vec3 parseVec3(const JsonValue::object_t& obj, const char* key, const lumina::Vec3& fallback);
lumina::ColorRGBA parseColor(const JsonValue::object_t& obj, const char* key, const lumina::ColorRGBA& fallback);

} // namespace lumina::json

#endif // LUMINA_JSON_PARSER_H
