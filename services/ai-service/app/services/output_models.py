"""Validated provider output models for recommendation responses."""

from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class RecommendationItem(BaseModel):
    """One normalized recommendation returned by an LLM provider."""

    model_config = ConfigDict(extra="ignore", str_strip_whitespace=True)

    title: str = Field(min_length=1, max_length=200)
    genre: str = Field(default="", max_length=200)
    description: str = Field(default="", max_length=2000)
    why_recommended: str = Field(default="", max_length=2000)
    platforms: list[str] = Field(default_factory=list, max_length=20)
    rating: float | None = Field(default=0.0, ge=0.0, le=10.0)
    release_year: str = Field(default="", max_length=20)

    @field_validator("platforms", mode="before")
    @classmethod
    def normalize_platforms(cls, value: Any) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            raise ValueError("platforms must be an array")
        return [str(item).strip() for item in value if str(item).strip()]

    @field_validator("rating", mode="before")
    @classmethod
    def normalize_rating(cls, value: Any) -> float:
        return 0.0 if value in (None, "") else value

    @field_validator("release_year", mode="before")
    @classmethod
    def normalize_release_year(cls, value: Any) -> str:
        return "" if value is None else str(value)


class RecommendationOutput(BaseModel):
    """Structured response accepted from the model after validation."""

    model_config = ConfigDict(extra="ignore", str_strip_whitespace=True)

    reply: str = Field(default="", max_length=8000)
    reasoning: str = Field(default="", max_length=8000)
    recommendations: list[RecommendationItem] = Field(default_factory=list, max_length=20)

    @model_validator(mode="after")
    def require_visible_content(self) -> "RecommendationOutput":
        if not self.reply and not self.reasoning and not self.recommendations:
            raise ValueError("response must contain reply, reasoning, or recommendations")
        return self
